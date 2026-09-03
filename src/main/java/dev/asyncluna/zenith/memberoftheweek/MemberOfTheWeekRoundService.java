package dev.asyncluna.zenith.memberoftheweek;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.newAggregation;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;

import dev.asyncluna.zenith.core.i18n.I18nManager;
import dev.asyncluna.zenith.core.i18n.SupportedLocale;
import dev.asyncluna.zenith.core.model.GuildSettings;
import dev.asyncluna.zenith.core.repository.GuildSettingsRepository;
import dev.asyncluna.zenith.discord.util.EmbedUtils;
import dev.asyncluna.zenith.memberoftheweek.config.MemberOfTheWeekProperties;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRound;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRoundStatus;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekVoteCount;
import dev.asyncluna.zenith.memberoftheweek.repository.MemberOfTheWeekRoundRepository;
import discord4j.common.util.Snowflake;
import discord4j.core.GatewayDiscordClient;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.object.entity.Message;
import discord4j.core.object.entity.channel.MessageChannel;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.core.spec.MessageCreateSpec;
import discord4j.rest.util.AllowedMentions;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemberOfTheWeekRoundService {
    public static final String COMPONENT_ID_PREFIX = "member_of_the_week_vote:";

    private final GatewayDiscordClient gatewayDiscordClient;
    private final MemberOfTheWeekProperties properties;
    private final MemberOfTheWeekRoundRepository roundRepository;
    private final GuildSettingsRepository guildSettingsRepository;
    private final I18nManager i18nManager;
    private final ReactiveMongoTemplate mongoTemplate;
    private final Clock memberOfTheWeekClock;

    public Mono<MemberOfTheWeekRound> rotateRound() {
        log.info("Rotating Member of the Week voting round");

        return isPaused()
                .flatMap(paused -> paused ? Mono.empty() : closeCurrentRound().then(openNewRound()))
                .doOnSuccess(round -> log.info(
                        "Member of the Week round rotated | roundId={} | endsAt={}", round.getId(), round.getEndsAt()))
                .doOnError(error -> log.error("Failed to rotate Member of the Week round", error));
    }

    public Mono<MemberOfTheWeekRound> openInitialRound() {
        log.info("Opening initial Member of the Week voting round");

        return isPaused()
                .flatMap(paused -> paused ? Mono.empty() : openNewRound())
                .doOnSuccess(round -> log.info(
                        "Initial Member of the Week round opened | roundId={} | endsAt={}",
                        round.getId(),
                        round.getEndsAt()));
    }

    public Mono<MemberOfTheWeekRound> getCurrentRound() {
        return roundRepository.findFirstByGuildIdAndStatusOrderByStartsAtDesc(
                properties.guildId(), MemberOfTheWeekRoundStatus.OPEN);
    }

    public Mono<MemberOfTheWeekRound> getCurrentRoundForGuild(String guildId) {
        return roundRepository.findFirstByGuildIdAndStatusOrderByStartsAtDesc(guildId, MemberOfTheWeekRoundStatus.OPEN);
    }

    public Mono<MemberOfTheWeekRound> getRound(String guildId, String roundId) {
        return roundRepository.findByIdAndGuildId(roundId, guildId);
    }

    public Flux<MemberOfTheWeekRound> getRounds(String guildId) {
        return roundRepository.findByGuildIdOrderByStartsAtDesc(guildId);
    }

    public Mono<MemberOfTheWeekRound> getLatestRound(String guildId) {
        return getRounds(guildId).next();
    }

    /**
     * Refreshes the persisted voting message without creating a second message.
     * This is useful after changing translations or embed formatting while a round
     * is still active.
     */
    public Mono<MemberOfTheWeekRound> refreshVotingMessage(MemberOfTheWeekRound round) {
        if (round.getMessageId() == null || round.getMessageId().isBlank()) {
            log.warn("Active Member of the Week round has no persisted message ID | roundId={}", round.getId());
            return Mono.just(round);
        }

        return getVotingChannel(round.getChannelId())
                .flatMap(channel -> channel.getMessageById(Snowflake.of(round.getMessageId())))
                .flatMap(message -> getGuildLocale().flatMap(locale -> {
                    String title = i18nManager.localize("member_of_the_week.embed.title", locale);
                    String description = createVotingDescription(round, locale);

                    boolean needsUpdate = message.getEmbeds().stream()
                            .findFirst()
                            .map(current -> !current.getTitle().orElse("").equals(title)
                                    || !current.getDescription().orElse("").equals(description))
                            .orElse(true);

                    if (!needsUpdate) {
                        return Mono.just(round);
                    }

                    EmbedCreateSpec updatedEmbed = EmbedCreateSpec.builder()
                            .color(EmbedUtils.DEFAULT_COLOR)
                            .title(title)
                            .description(description)
                            .timestamp(Instant.now(memberOfTheWeekClock))
                            .build();

                    return message.edit().withEmbeds(updatedEmbed).thenReturn(round);
                }))
                .doOnNext(refreshedRound -> log.info(
                        "Checked Member of the Week voting message | roundId={} | messageId={}",
                        refreshedRound.getId(),
                        refreshedRound.getMessageId()))
                .onErrorResume(error -> {
                    log.warn(
                            "Could not refresh Member of the Week voting message | roundId={} | messageId={}",
                            round.getId(),
                            round.getMessageId(),
                            error);
                    return Mono.just(round);
                });
    }

    public Mono<List<MemberOfTheWeekVoteCount>> getVoteCounts(String guildId, String roundId) {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("guildId").is(guildId).and("roundId").is(roundId)),
                group("candidateId").count().as("votes"),
                sort(Sort.Direction.DESC, "votes"));

        return aggregateVoteCounts(aggregation).collectList();
    }

    public Mono<List<MemberOfTheWeekVoteCount>> getAllVoteCounts(String guildId) {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("guildId").is(guildId)),
                group("candidateId").count().as("votes"),
                sort(Sort.Direction.DESC, "votes"));

        return aggregateVoteCounts(aggregation).collectList();
    }

    public Mono<Long> getVoteCount(String guildId, String roundId, String candidateId) {
        return mongoTemplate.count(
                Query.query(Criteria.where("guildId")
                        .is(guildId)
                        .and("roundId")
                        .is(roundId)
                        .and("candidateId")
                        .is(candidateId)),
                "member_of_the_week_votes");
    }

    public Mono<Long> getAllVoteCount(String guildId, String candidateId) {
        return mongoTemplate.count(
                Query.query(
                        Criteria.where("guildId").is(guildId).and("candidateId").is(candidateId)),
                "member_of_the_week_votes");
    }

    private Flux<MemberOfTheWeekVoteCount> aggregateVoteCounts(Aggregation aggregation) {
        return mongoTemplate
                .aggregate(aggregation, "member_of_the_week_votes", Document.class)
                .map(document -> {
                    Number votes = document.get("votes", Number.class);
                    return new MemberOfTheWeekVoteCount(document.getString("_id"), votes.longValue());
                });
    }

    public Mono<MemberOfTheWeekRound> openRound() {
        return isPaused()
                .flatMap(paused -> paused
                        ? Mono.empty()
                        : getCurrentRound()
                                .flatMap(round -> Mono.<MemberOfTheWeekRound>empty())
                                .switchIfEmpty(openNewRound()));
    }

    public Mono<MemberOfTheWeekRound> closeCurrentRound() {
        return roundRepository
                .findFirstByGuildIdAndStatusOrderByStartsAtDesc(properties.guildId(), MemberOfTheWeekRoundStatus.OPEN)
                .flatMap(this::closeRound)
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("No open Member of the Week round found to close");
                    return Mono.empty();
                }));
    }

    public Mono<MemberOfTheWeekRound> closeExpiredRound() {
        Instant now = Instant.now(memberOfTheWeekClock);

        return roundRepository
                .findFirstByGuildIdAndStatusOrderByStartsAtDesc(properties.guildId(), MemberOfTheWeekRoundStatus.OPEN)
                .filter(round -> round.getEndsAt() != null && !round.getEndsAt().isAfter(now))
                .flatMap(this::closeRound)
                .switchIfEmpty(Mono.defer(() -> {
                    log.debug("No expired Member of the Week round found");
                    return Mono.empty();
                }));
    }

    private Mono<MemberOfTheWeekRound> closeRound(MemberOfTheWeekRound round) {
        log.info("Closing Member of the Week round | roundId={}", round.getId());

        return Mono.zip(findWinners(round.getId()), countVotes(round.getId()))
                .flatMap(tuple -> announceResults(tuple.getT1(), tuple.getT2()))
                .then(Mono.defer(() -> {
                    round.setStatus(MemberOfTheWeekRoundStatus.CLOSED);

                    return roundRepository.save(round);
                }))
                .doOnSuccess(
                        closedRound -> log.info("Member of the Week round closed | roundId={}", closedRound.getId()));
    }

    public Mono<Boolean> isPaused() {
        return guildSettingsRepository
                .findById(properties.guildId())
                .map(GuildSettings::isMemberOfTheWeekPaused)
                .defaultIfEmpty(false);
    }

    public Mono<Void> pauseVoting() {
        return closeCurrentRound().then(setPaused(true));
    }

    public Mono<Void> unpauseVoting() {
        return setPaused(false);
    }

    private Mono<Void> setPaused(boolean paused) {
        return guildSettingsRepository
                .findById(properties.guildId())
                .defaultIfEmpty(GuildSettings.builder().id(properties.guildId()).build())
                .flatMap(settings -> {
                    settings.setMemberOfTheWeekPaused(paused);
                    return guildSettingsRepository.save(settings);
                })
                .then();
    }

    private Mono<MemberOfTheWeekRound> openNewRound() {
        Instant startsAt = Instant.now(memberOfTheWeekClock);
        Instant endsAt = startsAt.plus(properties.votingDuration());

        MemberOfTheWeekRound round = MemberOfTheWeekRound.builder()
                .guildId(properties.guildId())
                .channelId(properties.channelId())
                .startsAt(startsAt)
                .endsAt(endsAt)
                .status(MemberOfTheWeekRoundStatus.OPEN)
                .build();

        return roundRepository.save(round).flatMap(savedRound -> sendVotingMessage(savedRound)
                .flatMap(message -> {
                    savedRound.setMessageId(message.getId().asString());

                    return roundRepository.save(savedRound);
                })
                .onErrorResume(error -> {
                    log.error(
                            "Failed to send voting message; deleting newly created round | roundId={}",
                            savedRound.getId(),
                            error);

                    return roundRepository.delete(savedRound).then(Mono.error(error));
                }));
    }

    private Mono<Message> sendVotingMessage(MemberOfTheWeekRound round) {
        return getGuildLocale().flatMap(locale -> {
            SelectMenu selectMenu = SelectMenu.ofUser(COMPONENT_ID_PREFIX + round.getId(), Collections.emptyList())
                    .withPlaceholder(i18nManager.localize("member_of_the_week.select_member", locale))
                    .withMinValues(1)
                    .withMaxValues(1);

            EmbedCreateSpec embed = createVotingEmbed(round, locale);

            MessageCreateSpec.Builder messageBuilder =
                    MessageCreateSpec.builder().addEmbed(embed).addComponent(ActionRow.of(selectMenu));

            if (properties.roleId() != null && !properties.roleId().isBlank()) {
                Snowflake roleId = Snowflake.of(properties.roleId());

                messageBuilder
                        .content("<@&" + roleId.asString() + ">")
                        .allowedMentions(
                                AllowedMentions.builder().allowRole(roleId).build());
            }

            return getVotingChannel()
                    .flatMap(channel -> channel.createMessage(messageBuilder.build()))
                    .doOnSuccess(createdMessage -> log.info(
                            "Member of the Week voting message sent | channel={} | message={}",
                            properties.channelId(),
                            createdMessage.getId().asString()));
        });
    }

    private EmbedCreateSpec createVotingEmbed(MemberOfTheWeekRound round, Locale locale) {
        return EmbedCreateSpec.builder()
                .color(EmbedUtils.DEFAULT_COLOR)
                .title(i18nManager.localize("member_of_the_week.embed.title", locale))
                .description(createVotingDescription(round, locale))
                .timestamp(Instant.now(memberOfTheWeekClock))
                .build();
    }

    private String createVotingDescription(MemberOfTheWeekRound round, Locale locale) {
        String endsAt = MemberOfTheWeekTimeFormatter.format(round.getEndsAt(), memberOfTheWeekClock.getZone());

        return i18nManager.localize("member_of_the_week.embed.description", locale, endsAt);
    }

    private String createWinnerPingContent(MemberOfTheWeekVoteCount winner) {
        if (winner == null) {
            return null;
        }

        return "<@" + winner.candidateId() + ">";
    }

    private Mono<Void> announceResults(List<MemberOfTheWeekVoteCount> winners, long totalVotes) {
        MemberOfTheWeekVoteCount selectedWinner =
                winners.isEmpty() ? null : (winners.size() == 1 ? winners.getFirst() : pickRandomWinner(winners));
        return getGuildLocale().flatMap(locale -> {
            String description = createResultsDescription(winners, selectedWinner, totalVotes, locale);
            String content = createWinnerPingContent(selectedWinner);

            EmbedCreateSpec embed = EmbedCreateSpec.builder()
                    .color(EmbedUtils.DEFAULT_COLOR)
                    .title(i18nManager.localize("member_of_the_week.results.title", locale))
                    .description(description)
                    .timestamp(Instant.now(memberOfTheWeekClock))
                    .build();

            MessageCreateSpec.Builder messageBuilder =
                    MessageCreateSpec.builder().addEmbed(embed);

            if (content != null) {
                messageBuilder
                        .content(content)
                        .allowedMentions(AllowedMentions.builder()
                                .allowUser(Snowflake.of(selectedWinner.candidateId()))
                                .build());
            }

            return getVotingChannel()
                    .flatMap(channel -> channel.createMessage(messageBuilder.build()))
                    .doOnSuccess(message -> log.info(
                            "Member of the Week results announced | message={}",
                            message.getId().asString()))
                    .then();
        });
    }

    private String createResultsDescription(
            List<MemberOfTheWeekVoteCount> winners,
            MemberOfTheWeekVoteCount selectedWinner,
            long totalVotes,
            Locale locale) {
        String result;
        if (winners.isEmpty()) {
            result = i18nManager.localize("member_of_the_week.results.no_votes", locale);
        } else if (winners.size() == 1) {
            MemberOfTheWeekVoteCount winner = winners.getFirst();

            result = i18nManager.localize(
                    "member_of_the_week.results.winner",
                    locale,
                    "<@" + winner.candidateId() + ">",
                    winner.votes(),
                    i18nManager.localize(
                            winner.votes() == 1
                                    ? "member_of_the_week.results.vote"
                                    : "member_of_the_week.results.votes",
                            locale));
        } else {
            long voteCount = winners.getFirst().votes();

            String mentions = winners.stream()
                    .map(winner -> "<@" + winner.candidateId() + ">")
                    .reduce((first, second) -> first + ", " + second)
                    .orElse("");

            result = i18nManager.localize(
                    "member_of_the_week.results.tie",
                    locale,
                    mentions,
                    voteCount,
                    i18nManager.localize(
                            voteCount == 1 ? "member_of_the_week.results.vote" : "member_of_the_week.results.votes",
                            locale),
                    "<@" + selectedWinner.candidateId() + ">");
        }

        return result + "\n\n" + i18nManager.localize("member_of_the_week.results.total", locale, totalVotes);
    }

    private Mono<Locale> getGuildLocale() {
        return guildSettingsRepository
                .findById(properties.guildId())
                .map(GuildSettings::getLocale)
                .map(SupportedLocale::forLanguageTag)
                .map(SupportedLocale::getLocale)
                .defaultIfEmpty(SupportedLocale.ENGLISH.getLocale());
    }

    private Mono<Long> countVotes(String roundId) {
        return mongoTemplate.count(Query.query(Criteria.where("roundId").is(roundId)), "member_of_the_week_votes");
    }

    private MemberOfTheWeekVoteCount pickRandomWinner(List<MemberOfTheWeekVoteCount> winners) {
        return winners.get(ThreadLocalRandom.current().nextInt(winners.size()));
    }

    private Mono<List<MemberOfTheWeekVoteCount>> findWinners(String roundId) {
        Aggregation aggregation = newAggregation(
                match(Criteria.where("roundId").is(roundId)),
                group("candidateId").count().as("votes"),
                sort(Sort.Direction.DESC, "votes"));

        return mongoTemplate
                .aggregate(aggregation, "member_of_the_week_votes", Document.class)
                .map(document -> {
                    String candidateId = document.getString("_id");

                    Number votes = document.get("votes", Number.class);

                    return new MemberOfTheWeekVoteCount(candidateId, votes.longValue());
                })
                .collectList()
                .map(this::onlyHighestVoteCounts);
    }

    private List<MemberOfTheWeekVoteCount> onlyHighestVoteCounts(List<MemberOfTheWeekVoteCount> results) {
        if (results.isEmpty()) {
            return List.of();
        }

        long highestVoteCount = results.getFirst().votes();

        return results.stream()
                .filter(result -> result.votes() == highestVoteCount)
                .toList();
    }

    private Mono<MessageChannel> getVotingChannel() {
        return guildSettingsRepository
                .findById(properties.guildId())
                .map(GuildSettings::getMemberOfTheWeekChannelId)
                .filter(channelId -> channelId != null && !channelId.isBlank())
                .defaultIfEmpty(properties.channelId())
                .flatMap(this::getVotingChannel);
    }

    private Mono<MessageChannel> getVotingChannel(String configuredChannelId) {
        String channelId = configuredChannelId == null || configuredChannelId.isBlank()
                ? properties.channelId()
                : configuredChannelId;

        return gatewayDiscordClient
                .getChannelById(Snowflake.of(channelId))
                .ofType(MessageChannel.class)
                .switchIfEmpty(Mono.error(new IllegalStateException(
                        "Member of the Week channel does not exist or is not a message channel: " + channelId)));
    }
}
