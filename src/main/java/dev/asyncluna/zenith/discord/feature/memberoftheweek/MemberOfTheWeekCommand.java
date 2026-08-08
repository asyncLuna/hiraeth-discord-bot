package dev.asyncluna.zenith.discord.feature.memberoftheweek;

import dev.asyncluna.zenith.discord.command.BotCommand;
import dev.asyncluna.zenith.discord.command.Command;
import dev.asyncluna.zenith.discord.command.CommandContext;
import dev.asyncluna.zenith.discord.command.CommandException;
import dev.asyncluna.zenith.discord.command.CommandOption;
import dev.asyncluna.zenith.discord.command.SubCommand;
import dev.asyncluna.zenith.discord.util.DiscordConstants;
import dev.asyncluna.zenith.discord.util.EmbedUtils;
import dev.asyncluna.zenith.memberoftheweek.MemberOfTheWeekRoundService;
import dev.asyncluna.zenith.memberoftheweek.MemberOfTheWeekTimeFormatter;
import dev.asyncluna.zenith.memberoftheweek.model.MemberOfTheWeekRound;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.ChatInputAutoCompleteEvent;
import discord4j.core.object.command.ApplicationCommandInteractionOptionValue;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.User;
import discord4j.core.spec.EmbedCreateSpec;
import discord4j.discordjson.json.ApplicationCommandOptionChoiceData;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Command(
    name = "member_of_the_week",
    description = "Manage Member of the Week voting rounds.",
    defaultMemberPermissions = "8") // ADMINISTRATOR
@CommandOption(
    name = "open",
    description = "Open a new voting round.",
    type = ApplicationCommandOption.Type.SUB_COMMAND)
@CommandOption(
    name = "close",
    description = "Close the current round and announce its result.",
    type = ApplicationCommandOption.Type.SUB_COMMAND)
@CommandOption(
    name = "rotate",
    description = "Close the current round and immediately open a new one.",
    type = ApplicationCommandOption.Type.SUB_COMMAND)
@CommandOption(
    name = "status",
    description = "Show the currently open voting round.",
    type = ApplicationCommandOption.Type.SUB_COMMAND)
@CommandOption(
    name = "pause",
    description = "Pause voting and prevent new rounds from opening.",
    type = ApplicationCommandOption.Type.SUB_COMMAND)
@CommandOption(
    name = "unpause",
    description = "Resume automatic voting rounds.",
    type = ApplicationCommandOption.Type.SUB_COMMAND)
@CommandOption(
    name = "votes",
    description = "Show the vote count for a round.",
    type = ApplicationCommandOption.Type.SUB_COMMAND,
    subCommands = {
      @SubCommand(
          name = "member",
          description = "Member whose votes should be counted.",
          type = ApplicationCommandOption.Type.USER,
          required = true),
      @SubCommand(
          name = "round",
          description = "Round to inspect, or all rounds for a final total.",
          type = ApplicationCommandOption.Type.STRING,
          autocomplete = true)
    })
public class MemberOfTheWeekCommand implements BotCommand {
  private final MemberOfTheWeekRoundService roundService;
  private final Clock memberOfTheWeekClock;

  @Override
  public Mono<?> handle(CommandContext ctx) {
    if (hasSubcommand(ctx, "open")) return open(ctx);
    if (hasSubcommand(ctx, "close")) return close(ctx);
    if (hasSubcommand(ctx, "rotate")) return rotate(ctx);
    if (hasSubcommand(ctx, "status")) return status(ctx);
    if (hasSubcommand(ctx, "pause")) return pause(ctx);
    if (hasSubcommand(ctx, "unpause")) return unpause(ctx);
    if (hasSubcommand(ctx, "votes")) return votes(ctx);

    return Mono.error(new CommandException(ctx.localize("member_of_the_week.error.action")));
  }

  private Mono<?> votes(CommandContext ctx) {
    String guildId =
        ctx.getEvent().getInteraction().getGuildId().map(Snowflake::asString).orElse("");
    String requestedRound = ctx.getOptionAsString("round").orElse(null);
    Mono<User> member =
        ctx.getOptionAsUser("member")
            .orElseThrow(() -> new CommandException("A member must be selected."));

    return member.flatMap(
        selectedMember -> {
          String memberId = selectedMember.getId().asString();

          if ("all".equals(requestedRound)) {
            return roundService
                .getAllVoteCount(guildId, memberId)
                .flatMap(
                    count ->
                        ctx.editReply()
                            .withEmbeds(
                                formatVoteCount(
                                    ctx,
                                    "member_of_the_week.votes.all_title",
                                    null,
                                    memberId,
                                    count)));
          }

          Mono<MemberOfTheWeekRound> roundMono =
              requestedRound != null
                  ? roundService.getRound(guildId, requestedRound)
                  : roundService
                      .getCurrentRoundForGuild(guildId)
                      .switchIfEmpty(roundService.getLatestRound(guildId));

          return roundMono
              .switchIfEmpty(
                  Mono.error(
                      new CommandException(ctx.localize("member_of_the_week.error.no_rounds"))))
              .flatMap(
                  round ->
                      roundService
                          .getVoteCount(guildId, round.getId(), memberId)
                          .flatMap(
                              count ->
                                  ctx.editReply()
                                      .withEmbeds(
                                          formatVoteCount(
                                              ctx,
                                              "member_of_the_week.votes.round_title",
                                              round.getId(),
                                              memberId,
                                              count))));
        });
  }

  private EmbedCreateSpec formatVoteCount(
      CommandContext ctx, String titleKey, String titleArgument, String memberId, long count) {
    String title =
        titleArgument == null ? ctx.localize(titleKey) : ctx.localize(titleKey, titleArgument);
    return EmbedCreateSpec.builder()
        .color(EmbedUtils.DEFAULT_COLOR)
        .title(title)
        .description(
            ctx.localize("member_of_the_week.votes.description", "<@" + memberId + ">", count))
        .timestamp(Instant.now(memberOfTheWeekClock))
        .build();
  }

  @Override
  public Mono<Void> autocomplete(ChatInputAutoCompleteEvent event) {
    if (!"round".equals(event.getFocusedOption().getName())) {
      return event.respondWithSuggestions(List.of()).then();
    }

    String guildId = event.getInteraction().getGuildId().map(Snowflake::asString).orElse("");
    String input =
        event
            .getFocusedOption()
            .getValue()
            .map(ApplicationCommandInteractionOptionValue::asString)
            .orElse("")
            .toLowerCase();

    List<ApplicationCommandOptionChoiceData> allChoice =
        input.isBlank() || "all".contains(input)
            ? List.of(
                ApplicationCommandOptionChoiceData.builder()
                    .name("All rounds (final total)")
                    .value("all")
                    .build())
            : List.of();

    return roundService
        .getRounds(guildId)
        .filter(round -> round.getId() != null && round.getId().toLowerCase().contains(input))
        .take(DiscordConstants.MAX_AUTO_COMPLETE_RESULTS - allChoice.size())
        .map(
            round ->
                ApplicationCommandOptionChoiceData.builder()
                    .name(round.getId() + " (" + round.getStatus().name().toLowerCase() + ")")
                    .value(round.getId())
                    .build())
        .collectList()
        .map(
            rounds -> {
              List<ApplicationCommandOptionChoiceData> choices = new ArrayList<>(allChoice);
              choices.addAll(rounds);
              return choices;
            })
        .flatMap(event::respondWithSuggestions)
        .then();
  }

  private Mono<?> pause(CommandContext ctx) {
    return roundService
        .pauseVoting()
        .then(ctx.editReply(ctx.localize("member_of_the_week.paused")));
  }

  private Mono<?> unpause(CommandContext ctx) {
    return roundService
        .unpauseVoting()
        .then(ctx.editReply(ctx.localize("member_of_the_week.unpaused")));
  }

  private Mono<?> open(CommandContext ctx) {
    return roundService
        .isPaused()
        .flatMap(
            paused ->
                paused
                    ? Mono.error(
                        new CommandException(ctx.localize("member_of_the_week.error.paused")))
                    : roundService.getCurrentRound())
        .flatMap(
            round ->
                Mono.error(
                    new CommandException(
                        ctx.localize("member_of_the_week.error.already_open", round.getId()))))
        .switchIfEmpty(
            roundService
                .openRound()
                .flatMap(
                    round ->
                        ctx.editReply(ctx.localize("member_of_the_week.opened", round.getId()))));
  }

  private Mono<?> close(CommandContext ctx) {
    return roundService
        .closeCurrentRound()
        .flatMap(round -> ctx.editReply(ctx.localize("member_of_the_week.closed", round.getId())))
        .switchIfEmpty(
            Mono.error(
                new CommandException(ctx.localize("member_of_the_week.error.no_open_round"))));
  }

  private Mono<?> rotate(CommandContext ctx) {
    return roundService
        .rotateRound()
        .flatMap(round -> ctx.editReply(ctx.localize("member_of_the_week.rotated", round.getId())));
  }

  private Mono<?> status(CommandContext ctx) {
    return roundService
        .getCurrentRound()
        .flatMap(round -> ctx.editReply(formatStatus(ctx, round)))
        .switchIfEmpty(
            Mono.error(
                new CommandException(ctx.localize("member_of_the_week.error.no_open_round"))));
  }

  private String formatStatus(CommandContext ctx, MemberOfTheWeekRound round) {
    return ctx.localize(
        "member_of_the_week.status",
        round.getId(),
        MemberOfTheWeekTimeFormatter.format(round.getStartsAt(), memberOfTheWeekClock.getZone()),
        MemberOfTheWeekTimeFormatter.format(round.getEndsAt(), memberOfTheWeekClock.getZone()),
        round.getMessageId() == null ? "-" : round.getMessageId());
  }

  private boolean hasSubcommand(CommandContext ctx, String name) {
    return ctx.getEvent().getOption(name).isPresent();
  }
}
