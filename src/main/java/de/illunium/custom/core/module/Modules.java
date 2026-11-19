package de.illunium.custom.core.module;

import de.illunium.custom.beacons.BeaconModule;
import de.illunium.custom.emotes.EmotesModule;
import de.illunium.custom.groups.GroupsModule;
import de.illunium.custom.leaderboard.LeaderboardModule;
import de.illunium.custom.marriage.MarriageModule;
import de.illunium.custom.playtime.PlaytimeModule;
import de.illunium.custom.playtimecolors.PlaytimeColorsModule;
import de.illunium.custom.recipes.RecipesModule;
import de.illunium.custom.sit.SitModule;
import de.illunium.custom.spawn.SpawnModule;
import de.illunium.custom.vanish.VanishModule;
import de.illunium.custom.whitelistguard.WhitelistGuardModule;
import de.illunium.custom.whiteliststaging.WhitelistStagingModule;
import java.util.List;

/** Explicit module registry, in enable order. A new feature is added here and nowhere else in core. */
public final class Modules {

    private Modules() {
    }

    public static List<FeatureModule> create() {
        PlaytimeModule playtime = new PlaytimeModule();
        EmotesModule emotes = new EmotesModule();
        return List.of(
                new RecipesModule(),
                emotes,
                new GroupsModule(emotes),
                new MarriageModule(),
                playtime,
                new WhitelistGuardModule(),
                new WhitelistStagingModule(),
                new SitModule(),
                new VanishModule(),
                new SpawnModule(),
                new LeaderboardModule(playtime),
                new PlaytimeColorsModule(playtime),
                new BeaconModule()
        );
    }
}
