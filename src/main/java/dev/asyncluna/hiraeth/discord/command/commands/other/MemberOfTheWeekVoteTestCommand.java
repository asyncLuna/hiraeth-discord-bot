package dev.asyncluna.hiraeth.discord.command.commands.other;

import dev.asyncluna.hiraeth.discord.command.BotCommand;
import dev.asyncluna.hiraeth.discord.command.Command;
import dev.asyncluna.hiraeth.discord.command.CommandContext;
import dev.asyncluna.hiraeth.discord.util.EmbedUtils;
import discord4j.core.object.component.ActionRow;
import discord4j.core.object.component.SelectMenu;
import discord4j.core.spec.EmbedCreateSpec;
import java.util.Collections;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@Command(
    name = "member_of_the_week_vote_test",
    description = "Test the member of the week voting.",
    defaultMemberPermissions = "8" // ADMINISTRATOR
    )
public class MemberOfTheWeekVoteTestCommand implements BotCommand {
  @Override
  public Mono<?> handle(CommandContext ctx) {
    SelectMenu selectMenu =
        SelectMenu.ofUser("member_of_the_week_sm", Collections.emptyList())
            .withPlaceholder("Select a member to vote for");

    return ctx.editReply()
        .withEmbeds(
            EmbedCreateSpec.builder()
                .color(EmbedUtils.DEFAULT_COLOR)
                .title("It's voting time!")
                .description(
                    "Vote for the member of the week. You can only vote once, so choose wisely!")
                .build())
        .withComponents(ActionRow.of(selectMenu));
  }
}
