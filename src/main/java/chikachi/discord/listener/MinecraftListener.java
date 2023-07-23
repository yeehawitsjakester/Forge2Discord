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

package chikachi.discord.listener;

import chikachi.discord.core.CoreUtils;
import chikachi.discord.core.DiscordClient;
import chikachi.discord.core.Message;
import chikachi.discord.core.config.Configuration;
import chikachi.discord.core.config.minecraft.MinecraftConfig;
import chikachi.discord.core.config.minecraft.MinecraftDimensionConfig;
import chikachi.discord.core.config.types.MessageConfig;
import com.google.common.base.Joiner;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import net.dv8tion.jda.core.entities.User;
import net.minecraft.command.ICommandSender;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatisticsFile;
import net.minecraft.util.StatCollector;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.AchievementEvent;
import scala.util.Random;

import java.util.ArrayList;
import java.util.HashMap;

public class MinecraftListener {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onCommand(CommandEvent event) {
        if (event.isCanceled()) return;

        String commandName = event.command.getCommandName();
        ICommandSender sender = event.sender;

        MinecraftConfig minecraftConfig = Configuration.getConfig().minecraft;
        MinecraftDimensionConfig genericConfig = minecraftConfig.dimensions.generic;

        MessageConfig messageConfig;

        if (commandName.equalsIgnoreCase("say") || commandName.equalsIgnoreCase("me")) {
            boolean isSayCommand = commandName.equalsIgnoreCase("say");

            if (isSayCommand && !Configuration.getConfig().minecraft.dimensions.generic.relaySayCommand) {
                return;
            }

            if (!isSayCommand && !Configuration.getConfig().minecraft.dimensions.generic.relayMeCommand) {
                return;
            }

            if (sender != null && Configuration.getConfig().minecraft.dimensions.generic.ignoreFakePlayerChat && sender instanceof FakePlayer) {
                return;
            }

            String message = Joiner.on(" ").join(event.parameters);

            if (Configuration.getConfig().minecraft.dimensions.generic.isMessageIgnored(message)) {
                return;
            }

            HashMap<String, String> arguments = new HashMap<>();
            arguments.put("MESSAGE", isSayCommand ? message : "_" + message + "_");

            String prefix = minecraftConfig.dimensions.generic.chatPrefix;
            messageConfig = minecraftConfig.dimensions.generic.messages.chatMessage;

            ArrayList<Long> channels;

            if (sender != null) {
                Entity entity = sender.getEntityWorld().getPlayerEntityByName(sender.getCommandSenderName());

                if (entity != null) {
                    MinecraftDimensionConfig dimensionConfig = minecraftConfig.dimensions.getDimension(entity.dimension);

                    if (dimensionConfig.chatPrefix != null && dimensionConfig.chatPrefix.trim().length() > 0) {
                        prefix = dimensionConfig.chatPrefix;
                    }

                    if (dimensionConfig.messages.chatMessage != null) {
                        messageConfig = dimensionConfig.messages.chatMessage;
                    }

                    channels = dimensionConfig.relayChat.getChannels(
                        genericConfig.relayChat.getChannels(
                            dimensionConfig.discordChannel.getChannels(
                                genericConfig.discordChannel
                            )
                        )
                    );
                } else {
                    channels = genericConfig.relayChat.getChannels(genericConfig.discordChannel);
                }
            } else {
                channels = genericConfig.relayChat.getChannels(genericConfig.discordChannel);
            }

            String authorName = null;
            String avatarUrl = null;

            //noinspection Duplicates
            if (sender != null) {
                authorName = sender.getCommandSenderName();
                if (sender instanceof EntityPlayer) {
                    avatarUrl = CoreUtils.getAvatarUrl(sender.getCommandSenderName());

                    Long discordId = Configuration.getLinking().getDiscordId(((EntityPlayer) sender).getGameProfile().getId());
                    if (discordId != null) {
                        User discordUser = DiscordClient.getInstance().getUser(discordId);
                        if (discordUser != null) {
                            authorName = discordUser.getName();
                            avatarUrl = discordUser.getAvatarUrl();
                        }
                    }
                }
            }

            DiscordClient.getInstance().broadcast(
                new Message()
                    .setAuthor(authorName)
                    .setAvatarUrl(avatarUrl)
                    .setMessage(messageConfig)
                    .setArguments(arguments)
                    .setPrefix(prefix),
                channels
            );
        } else if (commandName.equalsIgnoreCase("discord")) {
            // Do not relay linking commands
            if (event.parameters.length > 0 && event.parameters[0].equalsIgnoreCase("link")) {
                return;
            }
        } else if (commandName.equalsIgnoreCase("list")) {
            //Ignore ApexMCs Link command being issued automatically every couple of minutes.
            //I may be at least 1% idiot...it's "link" not "list" I am trying to ignore. Oops
            return;
        } else if (commandName.equalsIgnoreCase("help")) {
            //Used for testing, dont need to see when people use help anyways.
            return;
        }

        ArrayList<Long> channels;

        messageConfig = minecraftConfig.dimensions.generic.messages.command;

        if (sender != null) {
            Entity entity = sender.getEntityWorld().getPlayerEntityByName(sender.getCommandSenderName());

            if (entity != null) {
                MinecraftDimensionConfig dimensionConfig = minecraftConfig.dimensions.getDimension(entity.dimension);

                if (dimensionConfig.messages.command != null) {
                    messageConfig = dimensionConfig.messages.command;
                }

                channels = dimensionConfig.relayCommands.getChannels(
                    genericConfig.relayCommands.getChannels(
                        dimensionConfig.discordChannel.getChannels(
                            genericConfig.discordChannel
                        )
                    )
                );
            } else {
                channels = genericConfig.relayCommands.getChannels(genericConfig.discordChannel);
            }
        } else {
            channels = genericConfig.relayCommands.getChannels(genericConfig.discordChannel);
        }

        HashMap<String, String> arguments = new HashMap<>();
        arguments.put("COMMAND", event.command.getCommandName());
        arguments.put("ARGUMENTS", Joiner.on(" ").join(event.parameters));

        String authorName = null;
        String avatarUrl = null;

        //noinspection Duplicates
        if (sender != null) {
            authorName = sender.getCommandSenderName();
            if (sender instanceof EntityPlayer) {
                avatarUrl = CoreUtils.getAvatarUrl(sender.getCommandSenderName());

                Long discordId = Configuration.getLinking().getDiscordId(((EntityPlayer) sender).getGameProfile().getId());
                if (discordId != null) {
                    User discordUser = DiscordClient.getInstance().getUser(discordId);
                    if (discordUser != null) {
                        authorName = discordUser.getName();
                        avatarUrl = discordUser.getAvatarUrl();
                    }
                }
            }
        }

        DiscordClient.getInstance().broadcast(
            new Message()
                .setAuthor(authorName)
                //TODO: Avatar URL doesn't seem to work.
                .setAvatarUrl( )
                .setMessage(messageConfig)
                .setArguments(arguments)
                .setParsing(false),
            channels
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onChatMessage(ServerChatEvent event) {
        if (event.isCanceled() || event.player == null) return;

        if (Configuration.getConfig().minecraft.dimensions.generic.ignoreFakePlayerChat && event.player instanceof FakePlayer) {
            return;
        }

        if (Configuration.getConfig().minecraft.dimensions.generic.isMessageIgnored(event.message)) {
            return;
        }

        HashMap<String, String> arguments = new HashMap<>();
        arguments.put("MESSAGE", event.message);

        MinecraftConfig minecraftConfig = Configuration.getConfig().minecraft;
        MinecraftDimensionConfig dimensionConfig = minecraftConfig.dimensions.getDimension(event.player.dimension);
        MinecraftDimensionConfig genericConfig = minecraftConfig.dimensions.generic;

        MessageConfig messageConfig = dimensionConfig.messages.chatMessage != null ? dimensionConfig.messages.chatMessage : genericConfig.messages.chatMessage;

        String authorName = event.username;
        String avatarUrl = CoreUtils.getAvatarUrl(authorName);

        Long discordId = Configuration.getLinking().getDiscordId(event.player.getGameProfile().getId());
        //noinspection Duplicates
        if (discordId != null) {
            User discordUser = DiscordClient.getInstance().getUser(discordId);
            if (discordUser != null) {
                authorName = discordUser.getName();
                avatarUrl = discordUser.getAvatarUrl();
            }
        }

        DiscordClient.getInstance().broadcast(
            new Message()
                .setAuthor(authorName)
                .setAvatarUrl(avatarUrl)
                .setMessage(messageConfig)
                .setArguments(arguments)
                .setPrefix(dimensionConfig.chatPrefix != null && dimensionConfig.chatPrefix.trim().length() > 0 ? dimensionConfig.chatPrefix : genericConfig.chatPrefix),
            dimensionConfig.relayChat.getChannels(
                genericConfig.relayChat.getChannels(
                    dimensionConfig.discordChannel.getChannels(
                        genericConfig.discordChannel
                    )
                )
            )
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerAchievement(AchievementEvent event) {
        if (event.isCanceled()) return;

        EntityPlayer entityPlayer = event.entityPlayer;

        if (entityPlayer != null && entityPlayer instanceof EntityPlayerMP) {
            StatisticsFile playerStats = ((EntityPlayerMP) entityPlayer).func_147099_x();

            if (playerStats.hasAchievementUnlocked(event.achievement) || !playerStats.canUnlockAchievement(event.achievement)) {
                return;
            }

            Achievement achievement = event.achievement;

            HashMap<String, String> arguments = new HashMap<>();
            arguments.put("ACHIEVEMENT", achievement.func_150951_e().getUnformattedText());
            arguments.put("DESCRIPTION", StatCollector.translateToLocalFormatted(achievement.achievementDescription, "KEY"));

            MinecraftConfig minecraftConfig = Configuration.getConfig().minecraft;
            MinecraftDimensionConfig dimensionConfig = minecraftConfig.dimensions.getDimension(entityPlayer.dimension);
            MinecraftDimensionConfig genericConfig = minecraftConfig.dimensions.generic;

            MessageConfig messageConfig = dimensionConfig.messages.achievement != null ? dimensionConfig.messages.achievement : genericConfig.messages.achievement;

            String authorName = entityPlayer.getDisplayName();
            String avatarUrl = CoreUtils.getAvatarUrl(authorName);

            Long discordId = Configuration.getLinking().getDiscordId(entityPlayer.getGameProfile().getId());
            //noinspection Duplicates
            if (discordId != null) {
                User discordUser = DiscordClient.getInstance().getUser(discordId);
                if (discordUser != null) {
                    authorName = discordUser.getName();
                    avatarUrl = discordUser.getAvatarUrl();
                }
            }

            DiscordClient.getInstance().broadcast(
                new Message()
                    .setAuthor(authorName)
                    .setAvatarUrl(avatarUrl)
                    .setMessage(messageConfig)
                    .setArguments(arguments),
                dimensionConfig.relayAchievements.getChannels(
                    genericConfig.relayAchievements.getChannels(
                        dimensionConfig.discordChannel.getChannels(
                            genericConfig.discordChannel
                        )
                    )
                )
            );
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.isCanceled() || event.player == null) return;

        MinecraftConfig minecraftConfig = Configuration.getConfig().minecraft;
        MinecraftDimensionConfig dimensionConfig = minecraftConfig.dimensions.getDimension(event.player.dimension);
        MinecraftDimensionConfig genericConfig = minecraftConfig.dimensions.generic;

        MessageConfig messageConfig = dimensionConfig.messages.playerJoin != null ? dimensionConfig.messages.playerJoin : genericConfig.messages.playerJoin;

        String authorName = event.player.getDisplayName();
        String avatarUrl = CoreUtils.getAvatarUrl(authorName);

        Long discordId = Configuration.getLinking().getDiscordId(event.player.getGameProfile().getId());
        //noinspection Duplicates
        if (discordId != null) {
            User discordUser = DiscordClient.getInstance().getUser(discordId);
            if (discordUser != null) {
                authorName = discordUser.getName();
                avatarUrl = discordUser.getAvatarUrl();
            }
        }

        DiscordClient.getInstance().broadcast(
            new Message()
                .setAuthor(authorName)
                .setAvatarUrl(avatarUrl)
                .setMessage(messageConfig),
            dimensionConfig.relayPlayerJoin.getChannels(
                genericConfig.relayPlayerJoin.getChannels(
                    dimensionConfig.discordChannel.getChannels(
                        genericConfig.discordChannel
                    )
                )
            )
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    //TODO: Check FML to see if player left on their own or if server/client had issues due to missing mods. If missing mods, report what mods in Discord from what end.
    public void onPlayerLeave(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.isCanceled() || event.player == null) return;

        MinecraftConfig minecraftConfig = Configuration.getConfig().minecraft;
        MinecraftDimensionConfig dimensionConfig = minecraftConfig.dimensions.getDimension(event.player.dimension);
        MinecraftDimensionConfig genericConfig = minecraftConfig.dimensions.generic;

        MessageConfig messageConfig = dimensionConfig.messages.playerLeave != null ? dimensionConfig.messages.playerLeave : genericConfig.messages.playerLeave;

        String authorName = event.player.getDisplayName();
        String avatarUrl = CoreUtils.getAvatarUrl(authorName);

        Long discordId = Configuration.getLinking().getDiscordId(event.player.getGameProfile().getId());
        //noinspection Duplicates
        if (discordId != null) {
            User discordUser = DiscordClient.getInstance().getUser(discordId);
            if (discordUser != null) {
                authorName = discordUser.getName();
                avatarUrl = discordUser.getAvatarUrl();
            }
        }

        DiscordClient.getInstance().broadcast(
            new Message()
                .setAuthor(authorName)
                .setAvatarUrl(avatarUrl)
                .setMessage(messageConfig),
            dimensionConfig.relayPlayerLeave.getChannels(
                genericConfig.relayPlayerLeave.getChannels(
                    dimensionConfig.discordChannel.getChannels(
                        genericConfig.discordChannel
                    )
                )
            )
        );
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerDeath(LivingDeathEvent event) {
        final String[] deathMessage = {
            "has been terminated abruptly.",
            "made a minor miscalculation",
            "was fucking obliterated.",
            "has deathed.", //dont you dare correct me on this
            "is no longer in this world",
            "has been summoned to Hell.",
            "tried to please a Protogen.",
            "summoned Herobrine unsuccessfully.",
            "was eaten by the Ender Dragon.",
            "was yoted.",
            "failed to rizz a Creeper into submission.",
            "was consumed by a deer.",
            "used his last Ender Pearl.",
            "installed his RAM backwards.",
            "has been hit with the Sound of Freedom by the UNITED STATES OF AMERICA BABY!",
            "knocked on the wrong door.",
            "went the wrong way in the labyrinth.",
            "Insert Text Here.",
            "tried to slam dunk a Zombie.",
            "did not find the way of God in time.",
            "had one too many warrants out for his arrest.",
            "touched the wrong block.",
            "wired two of the wrong cables together.",
            "cut the blue wire instead of the green wire.",
            "shit themselves.",
            "pissed under his desk to assert dominance to IKEA products.",
            "tried to feed the sergals.",
            "has [REDACTED]",
            "has undefined",
            "has null",
        };
        int rnd = new Random().nextInt(deathMessage.length);

        EntityLivingBase entityLiving = event.entityLiving;

        if (event.isCanceled() || entityLiving == null) return;

        if (entityLiving instanceof EntityPlayer) {
            EntityPlayer entityPlayer = (EntityPlayer) entityLiving;


            //TODO: Have a list of random death responses.
            //Update: We can't seem to extract *only* the entity that killed the player. This will be looked into later on, but for the time being this is our substitute
            HashMap<String, String> arguments = new HashMap<>();
            //is this arguments.put replacing the {REASON} in our death message to Discord?
            //yes, yes it is: <DEV> Player867 just died due to big whale balls!
            //what the fuck are these func's? why are these not named?!
            //if(EntityDamageSource.generic.getSourceOfDamage() != null) {
            // String killerEntity = entityPlayer.func_110142_aN().func_151521_b().getUnformattedText().replace(entityPlayer.getDisplayName(), "").trim();

            //}
            //System.out.println(EntityDamageSource.generic.getSourceOfDamage());

            arguments.put("REASON",":skull_crossbones: **"+entityPlayer.getDisplayName()+"**"+" "+deathMessage[rnd]);
            System.out.println(entityPlayer.getDisplayName()+" "+deathMessage[rnd]);
            //arguments.put("REASON", entityPlayer.func_110142_aN().func_151521_b().getUnformattedText().replace(entityPlayer.getDisplayName(), "").trim());

            MinecraftConfig minecraftConfig = Configuration.getConfig().minecraft;
            MinecraftDimensionConfig dimensionConfig = minecraftConfig.dimensions.getDimension(entityLiving.dimension);
            MinecraftDimensionConfig genericConfig = minecraftConfig.dimensions.generic;

            MessageConfig messageConfig = dimensionConfig.messages.playerDeath != null ? dimensionConfig.messages.playerDeath : genericConfig.messages.playerDeath;

            String authorName = entityPlayer.getDisplayName();
            String avatarUrl = CoreUtils.getAvatarUrl(authorName);

            Long discordId = Configuration.getLinking().getDiscordId(entityPlayer.getGameProfile().getId());
            //noinspection Duplicates
            if (discordId != null) {
                User discordUser = DiscordClient.getInstance().getUser(discordId);
                if (discordUser != null) {
                    authorName = discordUser.getName();
                    avatarUrl = discordUser.getAvatarUrl();
                }
            }

            DiscordClient.getInstance().broadcast(
                new Message()
                    .setAuthor(authorName)
                    .setAvatarUrl(avatarUrl)
                    .setMessage(messageConfig)
                    .setArguments(arguments),
                dimensionConfig.relayPlayerDeath.getChannels(
                    genericConfig.relayPlayerDeath.getChannels(
                        dimensionConfig.discordChannel.getChannels(
                            genericConfig.discordChannel
                        )
                    )
                )
            );
        }
    }
}
