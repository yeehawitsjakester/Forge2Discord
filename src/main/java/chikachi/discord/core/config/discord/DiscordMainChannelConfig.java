/*
 * Copyright (C) 2018 Chikachi and other contributors
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

package chikachi.discord.core.config.discord;

import com.google.gson.annotations.Since;

import java.util.HashMap;

public class DiscordMainChannelConfig {
    @Since(3.0)
    public DiscordChannelGenericConfig generic = new DiscordChannelGenericConfig();
    @Since(3.0)
    public HashMap<Long, DiscordChannelConfig> channels = new HashMap<>();

    public void fillFields() {
        if (this.generic == null) {
            this.generic = new DiscordChannelGenericConfig();
        }
        this.generic.fillFields();

        if (this.channels == null) {
            this.channels = new HashMap<>();
        }
        this.channels.values().forEach(DiscordChannelGenericConfig::fillFields);
    }
}
