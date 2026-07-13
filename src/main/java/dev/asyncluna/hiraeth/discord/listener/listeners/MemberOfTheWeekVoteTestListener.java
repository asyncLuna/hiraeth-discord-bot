package dev.asyncluna.hiraeth.discord.listener.listeners;

import dev.asyncluna.hiraeth.discord.listener.EventListener;
import discord4j.common.util.Snowflake;
import discord4j.core.event.domain.interaction.SelectMenuInteractionEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemberOfTheWeekVoteTestListener implements EventListener<SelectMenuInteractionEvent> {
  @Override
  public Mono<Void> execute(SelectMenuInteractionEvent event) {
    if (!event.getCustomId().equals("member_of_the_week_sm")) return Mono.empty();

    if (event.getValues().isEmpty())
      return event.reply("You must select a member to vote!").withEphemeral(true).then();

    String selectedUserId = event.getValues().getFirst();
    Snowflake selectedId = Snowflake.of(selectedUserId);
    Snowflake invokerId = event.getInteraction().getUser().getId();

    if (selectedId.equals(invokerId))
      return event.reply("You can't vote for yourself!").withEphemeral(true).then();

    return event
        .getInteraction()
        .getGuild()
        .flatMap(
            guild ->
                guild
                    .getMemberById(selectedId)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Member not found")))
                    .flatMap(
                        member -> {
                          if (member.isBot())
                            return event.reply("You can't vote for a bot!").withEphemeral(true);

                          log.info(
                              "Valid vote recorded | voter={} | votee={} ({})",
                              invokerId.asString(),
                              member.getDisplayName(),
                              selectedId.asString());

                          return event
                              .reply(
                                  "TEST: Your vote for **"
                                      + member.getDisplayName()
                                      + "** has been recorded!")
                              .withEphemeral(true);
                        })
                    .onErrorResume(
                        __ -> event.reply("Member not found in this guild.").withEphemeral(true)))
        .then();
  }
}
