package dev.asyncluna.hiraeth.discord.command.commands.moderation;

import dev.asyncluna.hiraeth.discord.command.BotCommand;
import dev.asyncluna.hiraeth.discord.command.Command;
import dev.asyncluna.hiraeth.discord.command.CommandContext;
import dev.asyncluna.hiraeth.discord.command.CommandException;
import dev.asyncluna.hiraeth.discord.command.CommandOption;
import discord4j.core.object.command.ApplicationCommandOption;
import discord4j.core.object.entity.Attachment;
import discord4j.core.object.entity.Guild;
import discord4j.core.object.entity.User;
import discord4j.core.spec.RoleCreateSpec;
import discord4j.rest.util.Color;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
@Slf4j
@Command(
    name = "create_role",
    description = "Create a colored role and assign it to a user.",
    defaultMemberPermissions = "268435456", // MANAGE_ROLES
    ephemeral = true)
@CommandOption(
    name = "role_name",
    description = "The name of the role to create.",
    type = ApplicationCommandOption.Type.STRING,
    required = true)
@CommandOption(
    name = "hex_color",
    description = "The role color as a hex code, with or without #.",
    type = ApplicationCommandOption.Type.STRING,
    required = true)
@CommandOption(
    name = "user",
    description = "The user to assign the role to.",
    type = ApplicationCommandOption.Type.USER,
    required = true)
@CommandOption(
    name = "icon",
    description = "Optional image attachment to use as the role icon.",
    type = ApplicationCommandOption.Type.ATTACHMENT)
public class CreateRoleCommand implements BotCommand {
  private static final Map<String, String> IMAGE_MIME_TYPES =
      Map.of(
          "png", "image/png",
          "jpg", "image/jpeg",
          "jpeg", "image/jpeg",
          "gif", "image/gif",
          "webp", "image/webp");
  private static final Pattern HEX_COLOR_PATTERN = Pattern.compile("^#?([0-9a-fA-F]{6})$");

  @Override
  public Mono<?> handle(CommandContext ctx) {
    String roleName = ctx.getOptionAsString("role_name").map(String::trim).orElse("");
    String rawHexColor = ctx.getOptionAsString("hex_color").map(String::trim).orElse("");
    Optional<Attachment> iconAttachment = ctx.getOptionAsAttachment("icon");

    if (roleName.isBlank())
      return Mono.error(new CommandException(ctx.localize("create_role.error.role_name_missing")));

    String normalizedHexColor = normalizeHexColor(ctx, rawHexColor);
    Color color = Color.of(Integer.parseInt(normalizedHexColor, 16));
    Mono<User> targetUserMono =
        ctx.getOptionAsUser("user")
            .orElseGet(
                () ->
                    Mono.error(
                        new CommandException(ctx.localize("create_role.error.user_not_found"))));
    Mono<Optional<String>> iconDataUriMono =
        iconAttachment
            .map(attachment -> downloadRoleIconDataUri(ctx, attachment).map(Optional::of))
            .orElseGet(() -> Mono.just(Optional.empty()));

    return ctx.getEvent()
        .getInteraction()
        .getGuild()
        .switchIfEmpty(
            Mono.error(new CommandException(ctx.localize("create_role.error.guild_only"))))
        .flatMap(
            guild ->
                Mono.zip(targetUserMono, iconDataUriMono)
                    .flatMap(
                        tuple ->
                            createRoleAndAssign(
                                ctx,
                                guild,
                                roleName,
                                normalizedHexColor,
                                color,
                                tuple.getT1(),
                                tuple.getT2())));
  }

  @SuppressWarnings("deprecation")
  private Mono<?> createRoleAndAssign(
      CommandContext ctx,
      Guild guild,
      String roleName,
      String normalizedHexColor,
      Color color,
      User user,
      Optional<String> iconDataUri) {
    RoleCreateSpec baseRoleSpec =
        RoleCreateSpec.create().withName(roleName).withColor(color).withMentionable(false);
    RoleCreateSpec roleSpec = iconDataUri.map(baseRoleSpec::withIcon).orElse(baseRoleSpec);

    return guild
        .getMemberById(user.getId())
        .switchIfEmpty(
            Mono.error(new CommandException(ctx.localize("create_role.error.user_not_found"))))
        .flatMap(
            member ->
                guild
                    .createRole(roleSpec)
                    .flatMap(role -> member.addRole(role.getId()).thenReturn(role))
                    .flatMap(
                        role ->
                            ctx.editReply(
                                ctx.localize(
                                    "create_role.success",
                                    role.getMention(),
                                    "#" + normalizedHexColor.toUpperCase(Locale.ROOT),
                                    user.getMention()))))
        .onErrorResume(
            exception -> {
              if (exception instanceof CommandException) {
                return Mono.error(exception);
              }

              log.error(
                  "Failed to create role '{}' and assign it to user '{}' in guild '{}'",
                  roleName,
                  user.getId().asString(),
                  guild.getId().asString(),
                  exception);
              return Mono.error(new CommandException(ctx.localize("create_role.error.failed")));
            });
  }

  private String normalizeHexColor(CommandContext ctx, String rawHexColor) {
    Matcher matcher = HEX_COLOR_PATTERN.matcher(rawHexColor);
    if (!matcher.matches())
      throw new CommandException(ctx.localize("create_role.error.invalid_color"));

    return matcher.group(1);
  }

  private Mono<String> downloadRoleIconDataUri(CommandContext ctx, Attachment attachment) {
    return Mono.defer(
        () -> {
          String mimeType =
              resolveImageMimeType(attachment)
                  .orElseThrow(
                      () -> new CommandException(ctx.localize("create_role.error.invalid_icon")));

          return WebClient.create()
              .get()
              .uri(attachment.getUrl())
              .retrieve()
              .bodyToMono(byte[].class)
              .map(
                  bytes ->
                      "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(bytes))
              .onErrorResume(
                  exception -> {
                    log.error(
                        "Failed to download role icon attachment '{}' from URL '{}'",
                        attachment.getFilename(),
                        attachment.getUrl(),
                        exception);
                    return Mono.error(
                        new CommandException(
                            ctx.localize("create_role.error.icon_download_failed")));
                  });
        });
  }

  private Optional<String> resolveImageMimeType(Attachment attachment) {
    Optional<String> contentType = attachment.getContentType();
    if (contentType.isPresent()
        && contentType.get().toLowerCase(Locale.ROOT).startsWith("image/")) {
      return contentType;
    }

    String filename = attachment.getFilename().toLowerCase(Locale.ROOT);
    int lastDotIndex = filename.lastIndexOf('.');
    if (lastDotIndex < 0 || lastDotIndex == filename.length() - 1) return Optional.empty();

    String extension = filename.substring(lastDotIndex + 1);
    return Optional.ofNullable(IMAGE_MIME_TYPES.get(extension));
  }
}
