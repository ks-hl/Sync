package dev.heliosares.sync.velocity;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.permission.Tristate;
import dev.kshl.kshlib.platform.ColorTranslator;
import net.kyori.adventure.chat.ChatType;
import net.kyori.adventure.chat.SignedMessage;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

public class CustomVelocityCommandSender implements CommandSource {
    private final Consumer<String> output;
    private final CommandSource handle;

    public CustomVelocityCommandSender(CommandSource handle, Consumer<String> output) {
        this.handle = handle;
        this.output = output;
    }

    @Override
    public boolean hasPermission(String permission) {
        return handle.hasPermission(permission);
    }

    @Override
    public Tristate getPermissionValue(String permission) {
        return handle.getPermissionValue(permission);
    }

    @Override
    public void sendMessage(@NotNull ComponentLike message) {
        output.accept(ColorTranslator.toString(message.asComponent()));
        handle.sendMessage(message);
    }

    @Override
    public void sendMessage(@NotNull Component message) {
        output.accept(ColorTranslator.toString(message.asComponent()));
        handle.sendMessage(message);
    }


    @Override
    public void sendMessage(@NotNull Component message, ChatType.@NotNull Bound boundChatType) {
        output.accept(ColorTranslator.toString(message));
        handle.sendMessage(message, boundChatType);
    }

    @Override
    public void sendMessage(@NotNull ComponentLike message, ChatType.@NotNull Bound boundChatType) {
        output.accept(ColorTranslator.toString(message.asComponent()));
        handle.sendMessage(message, boundChatType);
    }

    @Override
    public void sendMessage(@NotNull SignedMessage signedMessage, ChatType.@NotNull Bound boundChatType) {
        output.accept(signedMessage.message());
        handle.sendMessage(signedMessage, boundChatType);
    }

    @Override
    public void sendRichMessage(@NotNull String message) {
        output.accept(message);
        handle.sendRichMessage(message);
    }

    @Override
    public void sendRichMessage(@NotNull String message, @NotNull TagResolver @NotNull ... resolvers) {
        output.accept(message);
        handle.sendRichMessage(message, resolvers);
    }

    @Override
    public void sendPlainMessage(@NotNull String message) {
        output.accept(message);
        handle.sendPlainMessage(message);
    }
}
