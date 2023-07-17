/*
 * Copyright (C) 2017 Chikachi
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package chikachi.discord;

import chikachi.discord.core.Batcher;
import chikachi.discord.core.DiscordIntegrationLogger;
import chikachi.discord.core.Patterns;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.mojang.authlib.GameProfile;
import net.dv8tion.jda.core.entities.MessageChannel;
import net.dv8tion.jda.core.entities.User;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@SuppressWarnings("EntityConstructor")
@ParametersAreNonnullByDefault
public class DiscordCommandSender extends FakePlayer {
    private static final UUID playerUUID = UUID.fromString("f84c6a79-0a4e-45e0-879b-cd49ebd4c4e2");
    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(
        new ThreadFactoryBuilder()
            .setNameFormat(DiscordCommandSender.class.getSimpleName())
            .setDaemon(true)
            .build()
    );
    private final MessageChannel channel;
    private final Batcher<String> batcher = new Batcher<String>(this::sendBatch, 100, 10, executor);

    public DiscordCommandSender(MessageChannel channel, User user) {
        super(MinecraftServer.getServer().worldServers[0], new GameProfile(playerUUID, "@" + user.getName()));
        this.channel = channel;
    }

    @SuppressWarnings("unused")
    public DiscordCommandSender(WorldServer world, MessageChannel channel, String name) {
        super(world, new GameProfile(playerUUID, "@" + name));
        this.channel = channel;
    }

    @Override
    public boolean canCommandSenderUseCommand(int i, String s) {
        return true;
    }

    @Override
    public void addChatMessage(IChatComponent component) {
        Preconditions.checkNotNull(component);
        batcher.queue(textComponentToDiscordMessage(component));
    }

    @Override
    public void addChatComponentMessage(IChatComponent component) {
        Preconditions.checkNotNull(component);
        batcher.queue(textComponentToDiscordMessage(component));
    }

    private static String textComponentToDiscordMessage(IChatComponent component) {
        return Patterns.minecraftCodePattern.matcher(
            component.getUnformattedText()
        ).replaceAll("");
    }

    private void sendBatch(List<String> messages) {
        final int numMessages = messages.size();
        this.channel
            .sendMessage(
                Joiner.on("\n").join(messages)
            )
            .submit()
            .exceptionally((Throwable t) -> {
                // We could do some kind of retry here, but it feels like JDA should be responsible for that. Maybe it
                // already does.
                DiscordIntegrationLogger.Log(
                    "Exception sending " + numMessages + " messages to Discord:\n"
                        + Throwables.getStackTraceAsString(t),
                    true
                );
                return null;
            });
    }
}
