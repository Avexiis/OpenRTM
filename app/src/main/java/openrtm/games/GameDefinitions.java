package openrtm.games;

import com.jjrpc.JRPC;
import openrtm.console.ConsoleService;
import openrtm.util.HexUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class GameDefinitions {
    private GameDefinitions() {
    }

    public static List<GameDefinition> all() {
        return List.of(cod4(), waw(), mw2(), mw3(), bo1(), bo2(), bo3(), ghosts(), advancedWarfare());
    }

    private static GameDefinition cod4() {
        List<GameButton> buttons = new ArrayList<>();
        buttons.add(b("Lobby", "Force Start", "Cbuf: xpartygo", cbuf("xpartygo")));
        buttons.add(b("Lobby", "Fast Restart", "Cbuf: fast_restart", cbuf("fast_restart")));
        buttons.add(b("Lobby", "End Game", "Uses sv_serverid and sends endround", GameActions::cod4EndGame));
        buttons.add(b("Lobby", "Switch Team", "Uses the Team input value", GameActions::cod4SwitchTeam));
        buttons.add(b("Lobby", "Set Map", "Applies selected map code", GameActions::setMap));
        buttons.add(b("Lobby", "Set Mode", "Applies selected gametype code", GameActions::setGametype));
        buttons.add(b("Lobby", "Ranked Private Match", "Legacy private-match patch sequence", GameActions::cod4RankedPrivateMatch));
        buttons.add(b("Player", "Set FOV", "Cbuf: cg_fov", GameActions::setFov));
        buttons.add(b("Player", "Set MOTD", "Uses the Message input value", GameActions::cod4Motd));
        buttons.add(b("Player", "Set Clan Tag", "Cbuf: clanName and updategamerprofile", ctx -> {
            GameActions.cbuf(ctx, "clanName " + ctx.input("clantag"));
            GameActions.cbuf(ctx, "updategamerprofile");
        }));
        buttons.add(b("Player", "In-Game Gamertag", "Writes ASCII name at 0x84C24BBC", GameActions::cod4InGameGamertag));
        buttons.add(b("Player", "Offhost Gamertag", "Cbuf userinfo name", GameActions::cod4OffhostGamertag));
        buttons.add(b("Player", "Spoof Profile Gamertag", "Writes wide XAM gamertag buffer", GameActions::xamSpoof));
        buttons.add(b("Recovery", "Apply Stats", "Writes the recovery fields below", ctx -> GameActions.cod4ApplyStats(ctx.service(), ctx)));
        buttons.add(b("Recovery", "Clear Classes", "Writes -1 through the legacy class block", ctx -> {
            long address = 0x84C5F1E0L;
            for (int i = 0; i <= 0x44F; i++) {
                ctx.service().writeUInt32BE(address, 0xFFFF_FFFFL);
                address += 4;
            }
        }));

        List<GameToggle> toggles = List.of(
                t("Patches", "No Recoil", "NOP at 0x822EDAA0",
                        ctx -> nop(ctx.service(), 0x822EDAA0L),
                        ctx -> u32(ctx.service(), 0x822EDAA0L, 0x4803DD41L)),
                t("Patches", "Unlimited Ammo", "NOPs 0x8233169C and 0x8233048C",
                        ctx -> {
                            nop(ctx.service(), 0x8233169CL);
                            nop(ctx.service(), 0x8233048CL);
                        },
                        ctx -> {
                            u32(ctx.service(), 0x8233169CL, 0x4BFFB3DDL);
                            u32(ctx.service(), 0x8233048CL, 0x4BFFC3B5L);
                        }),
                t("Patches", "Super Jump", "NOP at 0x82319514",
                        ctx -> nop(ctx.service(), 0x82319514L),
                        ctx -> u32(ctx.service(), 0x82319514L, 0x48012F65L)),
                t("Patches", "Laser", "Patch at 0x823225C8",
                        ctx -> bytes(ctx.service(), 0x823225C8L, 0x3B, 0x40, 0, 1),
                        ctx -> bytes(ctx.service(), 0x823225C8L, 0x56, 0x3A, 6, 0x3E)),
                t("Patches", "Wallhack", "Writes 0x12/0x04 at 0x82303ECF",
                        ctx -> ctx.service().writeByte(0x82303ECFL, 0x12),
                        ctx -> ctx.service().writeByte(0x82303ECFL, 0x04)),
                t("Patches", "Force Host", "Legacy two-address host patch",
                        ctx -> {
                            nop(ctx.service(), 0x821A6AD0L);
                            bytes(ctx.service(), 0x822BD1F4L, 0x48, 0, 0, 0x34);
                        },
                        ctx -> {
                            bytes(ctx.service(), 0x821A6AD0L, 0x41, 0x99, 0, 0xA8);
                            bytes(ctx.service(), 0x822BD1F4L, 0x41, 0x9A, 0, 0x34);
                        }),
                t("Assists", "Aim Assist", "Cbuf aim_lockon_debug region patch",
                        cbuf("set aim_lockon_debug 1;aim_lockon_region_height 1;aim_lockon_region_width 1"),
                        cbuf("set aim_lockon_debug 0;aim_lockon_region_height 0;aim_lockon_region_width 0"))
        );

        return new GameDefinition(
                "Call of Duty 4",
                "0x415607E6",
                0x82239FD0L,
                new ClientLayout(0x8287CD08L, 0x278, 0x15C, 0x305C, 18),
                pairs(
                        "Backlot", "mp_backlot", "Countdown", "mp_countdown", "Shipment", "mp_shipment",
                        "Vacant", "mp_vacant", "District", "mp_citystreets", "Overgrown", "mp_overgrown",
                        "Strike", "mp_strike", "Crossfire", "mp_crossfire", "Crash", "mp_crash",
                        "Showdown", "mp_showdown", "Downpour", "mp_farm", "Pipeline", "mp_pipeline",
                        "Bloc", "mp_bloc", "Ambush", "mp_convoy", "Wet Work", "mp_cargoship", "Bog", "mp_bog"),
                pairs("Free for All", "dm", "Team Deathmatch", "war", "Search and Destroy", "sd", "Sabotage", "sab", "Domination", "dom", "Headquarters", "koth"),
                buttons,
                toggles,
                statFields(
                        "prestige", "Prestige", "10", "rank", "Rank", "55", "time", "Time", "0",
                        "assists", "Assists", "12526", "kills", "Kills", "153176", "wins", "Wins", "2341",
                        "killstreak", "Killstreak", "51", "score", "Score", "6542131", "winstreak", "Winstreak", "51",
                        "hits", "Hits", "95512", "misses", "Misses", "155126", "losses", "Losses", "1337",
                        "headshots", "Headshots", "7521", "deaths", "Deaths", "92415"),
                List.of(preset("Zero", "prestige", "0", "rank", "0", "time", "0", "assists", "0", "kills", "0", "wins", "0", "killstreak", "0", "score", "0", "winstreak", "0", "hits", "0", "misses", "0", "losses", "0", "headshots", "0", "deaths", "0")),
                GameActions::cod4ApplyStats);
    }

    private static GameDefinition mw2() {
        List<GameButton> buttons = new ArrayList<>();
        buttons.add(b("Lobby", "Force Start", "Private match partygo command", cbuf("set xblive_privatematch 1;wait 200;xpartygo;wait 200;set xblive_privatematch 0")));
        buttons.add(b("Lobby", "End Game", "Reads server id and sends endround", GameActions::mw2EndGame));
        buttons.add(b("Lobby", "Disable Game", "Writes branch patch at 0x822CC830", ctx -> GameActions.mw2DisableGame(ctx.service())));
        buttons.add(b("Lobby", "Set Map", "Applies selected map code", GameActions::setMap));
        buttons.add(b("Lobby", "Set Mode", "Applies selected gametype code", GameActions::setGametype));
        buttons.add(b("Player", "Set FOV", "Cbuf: cg_fov", GameActions::setFov));
        buttons.add(b("Player", "Send Game Command", "Runs the game command field", GameActions::rawCbuf));
        buttons.add(b("Player", "Spoof Profile Gamertag", "Writes wide XAM gamertag buffer", GameActions::xamSpoof));
        buttons.add(b("Title Text", "Barnacle Boy", "Writes title text buffer at 0x838BA824", ctx -> fixedAscii(ctx.service(), 0x838BA824L, "Barnacle Boy", 32)));
        buttons.add(b("Title Text", "Top Suspect", "Writes title text buffer at 0x838BA824", ctx -> fixedAscii(ctx.service(), 0x838BA824L, "Top Suspect", 32)));
        buttons.add(b("Title Text", "Build Expired", "Writes localized error key at 0x838BA824", ctx -> fixedAscii(ctx.service(), 0x838BA824L, "@MP_BUILDEXPIRED", 32)));
        buttons.add(b("Title Text", "Restore Title Text", "Copies 19 bytes into 0x838BA824", ctx -> ctx.service().writeMemory(0x838BA824L, ctx.service().readMemory(0x831A13A4L, 19))));
        buttons.add(b("Recovery", "Unlock All", "Legacy server-command challenge stream", ctx -> GameActions.mw2UnlockAll(ctx.service())));
        buttons.add(b("Recovery", "Apply Stats", "Writes rank XP, prestige, and score", ctx -> GameActions.mw2ApplyRecoveryStats(ctx.service(), ctx)));
        buttons.add(b("Clients", "Red Boxes Off", "Applies to selected or all clients", ctx -> eachClient(ctx, i -> GameActions.mw2Redboxes(ctx.service(), ctx.game().clients(), i, false))));

        List<GameToggle> toggles = List.of(
                t("Patches", "No Recoil", "Writes 0/7 at 0x82135BE3",
                        ctx -> ctx.service().writeMemory(0x82135BE3L, new byte[1]),
                        ctx -> ctx.service().writeByte(0x82135BE3L, 7)),
                t("Patches", "Laser", "NOPs 0x820E5B38 and 0x820E657C",
                        ctx -> {
                            nop(ctx.service(), 0x820E5B38L);
                            nop(ctx.service(), 0x820E657CL);
                        },
                        ctx -> {
                            u32(ctx.service(), 0x820E5B38L, 0x4BFFEAA9L);
                            u32(ctx.service(), 0x820E657CL, 0x4BFFFBC5L);
                        }),
                t("Patches", "Big Names", "NOPs 0x820E6570 and 0x82128AA8",
                        ctx -> {
                            nop(ctx.service(), 0x820E6570L);
                            nop(ctx.service(), 0x82128AA8L);
                        },
                        ctx -> {
                            u32(ctx.service(), 0x820E6570L, 0x4BFFF989L);
                            u32(ctx.service(), 0x82128AA8L, 0x4BFFA970L);
                        }),
                t("Patches", "No Flash", "Writes stun and flashback shock structs",
                        ctx -> GameActions.mw2NoFlash(ctx.service(), true),
                        ctx -> GameActions.mw2NoFlash(ctx.service(), false)),
                t("Patches", "FPS Counter", "Writes 15/4 at 0x821123A7",
                        ctx -> ctx.service().writeByte(0x821123A7L, 15),
                        ctx -> ctx.service().writeByte(0x821123A7L, 4)),
                t("Patches", "Wallhack", "Writes 0/1 at 0x82127CFF",
                        ctx -> ctx.service().writeMemory(0x82127CFFL, new byte[1]),
                        ctx -> ctx.service().writeByte(0x82127CFFL, 1)),
                t("Patches", "Cheats", "Writes 1/0 at 0x820E01AF",
                        ctx -> ctx.service().writeByte(0x820E01AFL, 1),
                        ctx -> ctx.service().writeByte(0x820E01AFL, 0)),
                t("Commands", "VSAT", "Cbuf g_compassShowEnemies",
                        cbuf("g_compassShowEnemies 1"),
                        cbuf("g_compassShowEnemies 0")),
                t("Commands", "Third Person", "Cbuf cg_thirdperson",
                        cbuf("cg_thirdperson 1"),
                        cbuf("cg_thirdperson 0")),
                t("Commands", "Fullbright", "Cbuf r_fullbright",
                        cbuf("r_fullbright 1"),
                        cbuf("r_fullbright 0")),
                t("Commands", "Force Host", "Legacy host dvar bundle",
                        cbuf("party_connectToOthers 1; partyMigrate_disabled 1; sv_endGameIfISuck 0; badhost_endgameifisuck 0; party_maxTeamDiff 8; set party_minplayers 18; set party_connectTimeout 1; party_hostmigration 0"),
                        cbuf("set party_minplayers 6; set party_gamestarttimelength 10; set party_pregamestarttimerlength 10; set party_timer 10; set party_connectTimeout 2500")),
                t("Commands", "Super Speed", "Server command g_speed",
                        ctx -> {
                            GameActions.mw2ServerCommand(ctx.service(), -1, "s g_speed 400");
                            GameActions.mw2ServerCommand(ctx.service(), -1, "f \"^1Super Speed: ^7ON\"");
                        },
                        ctx -> {
                            GameActions.mw2ServerCommand(ctx.service(), -1, "s g_speed 175");
                            GameActions.mw2ServerCommand(ctx.service(), -1, "f \"^1Super Speed: ^7OFF\"");
                        })
        );

        return new GameDefinition(
                "Modern Warfare 2",
                "0x41560817",
                0x82224990L,
                new ClientLayout(0x82F03600L, 640, 0x158, 0x3290, 18),
                pairs(
                        "Favela", "mp_favela", "Highrise", "mp_highrise", "Scrapyard", "mp_boneyard",
                        "Karachi", "mp_checkpoint", "Underpass", "mp_underpass", "Invasion", "mp_invasion",
                        "Wasteland", "mp_brecourt", "Terminal", "mp_terminal", "Quarry", "mp_quarry",
                        "Estate", "mp_estate", "Rust", "mp_rust", "Sub Base", "mp_subbase",
                        "Derail", "mp_derail", "Skidrow", "mp_nightshift", "Afghan", "mp_afghan", "Rundown", "mp_rundown"),
                pairs("Free for All", "dm", "Team Deathmatch", "war", "Search and Destroy", "sd", "Sabotage", "sab", "Domination", "dom", "Headquarters", "koth", "Capture The Flag", "ctf", "Demolition", "dd", "GTW", "gtnw"),
                buttons,
                toggles,
                statFields("rank", "Rank", "70", "prestige", "Prestige", "10", "score", "Score", "6542131"),
                List.of(preset("Zero", "rank", "1", "prestige", "0", "score", "0")),
                GameActions::mw2ApplyRecoveryStats);
    }

    private static GameDefinition mw3() {
        List<GameButton> buttons = new ArrayList<>();
        buttons.add(b("Lobby", "Force Start", "Cbuf: xpartygo", cbuf("xpartygo")));
        buttons.add(b("Lobby", "Fast Restart", "Cbuf: fast_restart", cbuf("fast_restart")));
        buttons.add(b("Lobby", "Set Map", "Applies selected map code", GameActions::setMap));
        buttons.add(b("Lobby", "Set Mode", "Applies selected gametype code", GameActions::setGametype));
        buttons.add(b("Player", "Set FOV", "Cbuf: cg_fov", GameActions::setFov));
        buttons.add(b("Player", "Send Game Command", "Runs the game command field", GameActions::rawCbuf));
        buttons.add(b("Player", "3D Name", "Writes 0x0D split name to 0x839691AC", ctx -> GameActions.mw3ThreeDeeName(ctx.service(), ctx.input("line1"), ctx.input("line2"))));
        buttons.add(b("Recovery", "Apply Stats", "Writes the recovery fields below", ctx -> GameActions.mw3ApplyStats(ctx.service(), ctx)));
        buttons.add(b("Recovery", "Unlock All", "Embedded memory payload from ported data", ctx -> PayloadActions.mw3UnlockAll(ctx.service())));
        buttons.add(b("Recovery", "Godmode Classes", "Embedded memory payload from ported data", ctx -> PayloadActions.mw3GodmodeClasses(ctx.service())));
        buttons.add(b("Recovery", "Custom Class Names", "Writes ten class names", ctx -> GameActions.mw3CustomClasses(ctx.service())));
        buttons.add(b("Recovery", "Add AUG Class", "Writes 8 bytes at 0x830A70F3", ctx -> bytes(ctx.service(), 0x830A70F3L, 1, 0x5A, 0, 0x11, 0, 0, 0, 5)));
        buttons.add(b("Recovery", "Set Ten Classes", "Writes 0x0A at 0x830A8BD3", ctx -> ctx.service().writeByte(0x830A8BD3L, 0x0A)));
        buttons.add(b("Recovery", "Unlock Extra Classes", "Writes 0xFF at 0x830A8BDC", ctx -> ctx.service().writeByte(0x830A8BDCL, 0xFF)));
        buttons.add(b("Recovery", "Gold Clan Tag", "Writes 1 at 0x82718A37", ctx -> ctx.service().writeByte(0x82718A37L, 1)));
        buttons.add(b("Recovery", "Elite Clan Tag", "Writes 1 at 0x82718A38", ctx -> ctx.service().writeByte(0x82718A38L, 1)));
        buttons.add(b("Recovery", "Patch Clan Tag", "Writes 0xFF at 0x82718A38", ctx -> ctx.service().writeByte(0x82718A38L, 0xFF)));
        buttons.add(b("Patches", "Force Host Patch", "One-way legacy host patch", ctx -> {
            bytes(ctx.service(), 0x823BC98CL, 0x48, 0, 0, 0xC0);
            bytes(ctx.service(), 0x8219A500L, 0x48, 0, 0, 0x68);
            ctx.service().xNotify("Cannot untoggle...", JRPC.XNotiyLogo.FLASHING_XBOX_CONSOLE);
        }));

        List<GameToggle> toggles = List.of(
                t("Patches", "No Recoil", "NOP at 0x821614D4",
                        ctx -> nop(ctx.service(), 0x821614D4L),
                        ctx -> bytes(ctx.service(), 0x821614D4L, 0x4B, 0xFA, 0x03, 0x95)),
                t("Patches", "Laser", "NOP at 0x821154A4",
                        ctx -> nop(ctx.service(), 0x821154A4L),
                        ctx -> bytes(ctx.service(), 0x821154A4L, 0x41, 0x9A, 0, 0x0C)),
                t("Patches", "FPS Counter", "Writes 15/4 at 0x82135107",
                        ctx -> ctx.service().writeByte(0x82135107L, 15),
                        ctx -> ctx.service().writeByte(0x82135107L, 4)),
                t("Patches", "VSAT", "Patches 0x8210E58C",
                        ctx -> bytes(ctx.service(), 0x8210E58CL, 0x3B, 0x80, 0, 1),
                        ctx -> bytes(ctx.service(), 0x8210E58CL, 0x55, 0x7C, 0x87, 0xFE)),
                t("Patches", "FOV Override", "Writes 0x80700000/0x43700000",
                        ctx -> bytes(ctx.service(), 0x82000B68L, 0x80, 0x70, 0, 0),
                        ctx -> bytes(ctx.service(), 0x82000B68L, 0x43, 0x70, 0, 0))
        );

        return new GameDefinition(
                "Modern Warfare 3",
                "0x415608CB",
                0x82287EE0L,
                new ClientLayout(0x82DCCC80L, 640, 0x158, 0x3414, 18),
                pairs("Dome", "mp_dome", "Lockdown", "mp_alpha", "Bootleg", "mp_bootleg", "Mission", "mp_bravo", "Carbon", "mp_carbon", "Hardhat", "mp_hardhat", "Interchange", "mp_interchange", "Fallen", "mp_lambeth", "Bakaara", "mp_mogadishu", "Resistance", "mp_paris", "Arkaden", "mp_plaza2", "Outpost", "mp_radar", "Seatown", "mp_seatown", "Underground", "mp_underground", "Village", "mp_village"),
                pairs("Free for All", "dm", "Team Deathmatch", "war", "Search and Destroy", "sd", "Sabotage", "sab", "Domination", "dom", "Headquarters", "koth", "Capture The Flag", "ctf", "Demolition", "dd", "Kill Confirmed", "conf"),
                buttons,
                toggles,
                statFields("prestige", "Prestige", "20", "tokens", "Tokens", "69", "score", "Score", "6542131", "wins", "Wins", "2341", "losses", "Losses", "1337", "headshots", "Headshots", "7521", "kills", "Kills", "153176", "deaths", "Deaths", "92415", "killstreak", "Killstreak", "51", "assists", "Assists", "12526"),
                List.of(preset("Zero", "prestige", "0", "tokens", "0", "score", "0", "wins", "0", "losses", "0", "headshots", "0", "kills", "0", "deaths", "0", "killstreak", "0", "assists", "0")),
                GameActions::mw3ApplyStats);
    }

    private static GameDefinition bo1() {
        List<GameButton> buttons = new ArrayList<>();
        buttons.add(b("Lobby", "End Game", "Reads server id and sends endround", GameActions::bo1EndGame));
        buttons.add(b("Lobby", "Fast Restart", "Cbuf: fast_restart", cbuf("fast_restart")));
        buttons.add(b("Lobby", "Set Map", "Applies selected map code", GameActions::setMap));
        buttons.add(b("Lobby", "Set Mode", "Applies selected gametype code", GameActions::setGametype));
        buttons.add(b("Player", "Set FOV", "Cbuf: cg_fov", GameActions::setFov));
        buttons.add(b("Player", "Send Game Command", "Runs the game command field", GameActions::rawCbuf));
        buttons.add(b("Player", "In-Game Gamertag", "Cbuf userinfo name", GameActions::bo1InGameGamertag));
        buttons.add(b("Player", "Spoof Profile Gamertag", "Writes wide XAM gamertag buffer", GameActions::xamSpoof));
        buttons.add(b("Recovery", "Apply Stats", "Writes the recovery fields below", ctx -> GameActions.bo1ApplyStats(ctx.service(), ctx)));
        buttons.add(b("Recovery", "Dumb Stats", "Writes the legacy zero-block stat sequence", ctx -> GameActions.bo1DumbStats(ctx.service())));
        buttons.add(b("Recovery", "Unlock Achievements", "Legacy achievement server-command stream", ctx -> GameActions.bo1UnlockAchievements(ctx.service())));
        buttons.add(b("Recovery", "Custom Classes", "Cbuf customclass names and uploadstats", ctx -> {
            GameActions.cbuf(ctx, "customclass1 ^5Classes;customclass2 ^2Set;customclass3 ^3By;customclass4 ^6KingCalzones;customclass5 ^1Multi-Tool;customclass6 ^Lets;customclass7 ^8Fucking;customclass8 ^2Roll;customclass9 ^2Cheater;customclass10 ^2Bitches! :)");
            GameActions.cbuf(ctx, "uploadstats; updategamerprofile");
            ctx.service().xNotify("BO1\nStats Sent", JRPC.XNotiyLogo.FLASHING_XBOX_CONSOLE);
        }));
        buttons.add(b("Recovery", "Legacy Class Unlock", "Embedded address/data payload", ctx -> PayloadActions.bo1LegacyClassUnlock(ctx.service())));
        buttons.add(b("Playercard", "Preset A", "Writes playercard bytes at 0x84085A7A/0x84085A80", ctx -> {
            ctx.service().writeMemory(0x84085A7AL, new byte[1]);
            bytes(ctx.service(), 0x84085A80L, 0, 13, 0, 0, 64, 0, 240, 0, 240, 3, 0, 0, 0, 0, 15);
        }));
        buttons.add(b("Playercard", "Preset B", "Writes 19 bytes at 0x84085A9F", ctx -> bytes(ctx.service(), 0x84085A9FL, 86, 2, 0, 16, 118, 2, 0, 0, 0, 16, 240, 3, 102, 2, 0, 0, 0, 134, 2)));
        buttons.add(b("Playercard", "Preset C", "Writes playercard bytes at 0x84085A7A/0x84085A9A", ctx -> {
            ctx.service().writeMemory(0x84085A7AL, new byte[1]);
            bytes(ctx.service(), 0x84085A9AL, 3, 18, 124, 68, 52, 230);
        }));
        buttons.add(b("Playercard", "Preset D", "Writes 19 bytes at 0x84085A9F", ctx -> bytes(ctx.service(), 0x84085A9FL, 134, 2, 0, 16, 38, 3, 0, 0, 0, 16, 240, 3, 182, 2, 0, 0, 0, 166, 1)));
        buttons.add(b("Playercard", "Random Card Bytes", "Writes randomized playercard bytes", ctx -> GameActions.bo1RandomizeCardBytes(ctx.service())));
        buttons.add(b("Playercard", "Random Background", "Writes randomized 26-byte background data", ctx -> GameActions.bo1RandomizeBackground(ctx.service())));

        List<GameToggle> toggles = List.of(
                t("Patches", "No Recoil", "NOP at 0x82227624",
                        ctx -> nop(ctx.service(), 0x82227624L),
                        ctx -> u32(ctx.service(), 0x82227624L, 0x4BF67EC5L)),
                t("Patches", "Red Boxes", "Writes 1/0 at 0x821A819F",
                        ctx -> ctx.service().writeByte(0x821A819FL, 1),
                        ctx -> ctx.service().writeByte(0x821A819FL, 0)),
                t("Patches", "Red Boxes + VSAT", "Writes 1/0 at 0x821A819F and 0x821DA22B",
                        ctx -> {
                            ctx.service().writeByte(0x821A819FL, 1);
                            ctx.service().writeByte(0x821DA22BL, 1);
                        },
                        ctx -> {
                            ctx.service().writeByte(0x821A819FL, 0);
                            ctx.service().writeByte(0x821DA22BL, 0);
                        }),
                t("Patches", "FPS Counter", "Patches 0x821DFEF8",
                        ctx -> bytes(ctx.service(), 0x821DFEF8L, 0x38, 0xC0, 1, 15),
                        ctx -> bytes(ctx.service(), 0x821DFEF8L, 0x7F, 0xA6, 0xEB, 120)),
                t("Commands", "Force Host", "Legacy party-host dvar bundle",
                        cbuf("set party_connectToOthers 0;set party_minplayers 1;set party_gamestarttimelength 1;set party_pregamestarttimerlength 1;set party_connectTimeout 1"),
                        cbuf("set party_minplayers 8;set party_gamestarttimelength 10;set party_pregamestarttimerlength 10;set party_connectTimeout 2500"))
        );

        return new GameDefinition(
                "Black Ops",
                "0x41560855",
                0x8233E8D8L,
                new ClientLayout(0x82EDE540L, 760, 0x144, 0x27F8, 18),
                pairs("Nuketown", "mp_nuked", "Firing Range", "mp_firingrange", "Array", "mp_array", "Launch", "mp_cosmodrome", "Radiation", "mp_radiation", "Grid", "mp_duga", "Summit", "mp_mountain", "WMD", "mp_russianbase", "Cracked", "mp_cracked", "Havana", "mp_cairo", "Jungle", "mp_havoc", "Villa", "mp_villa", "Crisis", "mp_crisis", "Hanoi", "mp_hanoi"),
                pairs("Free for All", "dm", "Team Deathmatch", "tdm", "Search and Destroy", "sd", "Sabotage", "sab", "Domination", "dom", "Headquarters", "koth", "Capture The Flag", "ctf", "Demolition", "dem"),
                buttons,
                toggles,
                statFields("prestige", "Prestige", "15", "rank", "Rank", "50", "kills", "Kills", "153176", "deaths", "Deaths", "92415", "score", "Score", "6542131", "headshots", "Headshots", "7521", "wins", "Wins", "2341", "losses", "Losses", "1337", "assists", "Assists", "12526", "codpoints", "COD Points", "999999"),
                List.of(preset("Zero", "prestige", "0", "rank", "0", "kills", "0", "deaths", "0", "score", "0", "headshots", "0", "wins", "0", "losses", "0", "assists", "0", "codpoints", "0")),
                GameActions::bo1ApplyStats);
    }

    private static GameDefinition bo2() {
        List<GameButton> buttons = new ArrayList<>();
        buttons.add(b("Lobby", "Force Host Start", "Cbuf: xstartpartyhost", cbuf("xstartpartyhost")));
        buttons.add(b("Lobby", "Force Start", "Cbuf: xstartparty", cbuf("xstartparty")));
        buttons.add(b("Lobby", "Fast Restart", "Cbuf: fast_restart", cbuf("fast_restart")));
        buttons.add(b("Lobby", "Set Map", "Applies selected map code", GameActions::setMap));
        buttons.add(b("Player", "Set FOV", "Cbuf: cg_fov", GameActions::setFov));
        buttons.add(b("Player", "Send Game Command", "Runs the game command field", GameActions::rawCbuf));
        buttons.add(b("Player", "Set In-Game Gamertag", "Installs name hook and writes ASCII name", ctx -> GameActions.bo2SetGamertag(ctx.service(), ctx.input("gamertag"))));
        buttons.add(b("Player", "Spoof Profile Gamertag", "Writes wide XAM gamertag buffer", GameActions::xamSpoof));
        buttons.add(b("Player", "Spoof IP", "Writes four IP bytes at 0xC24313E0", ctx -> ctx.service().spoofIp(ctx.input("ip"))));
        buttons.add(b("Recovery", "Apply Stats", "Writes the recovery fields below", ctx -> GameActions.bo2ApplyStats(ctx.service(), ctx)));
        buttons.add(b("Recovery", "Bypass Version Checks", "NOPs version checks", ctx -> GameActions.bo2PatchVersion(ctx.service())));
        buttons.add(b("Recovery", "Enable DLC Weapon Classes", "Writes bytes at 0x84352AC8 and 0x826A5FBC", ctx -> {
            bytes(ctx.service(), 0x84352AC8L, 0xC0, 0x3F);
            bytes(ctx.service(), 0x826A5FBCL, 0x3B, 0x40, 0xCB, 0xE7);
        }));
        buttons.add(b("Recovery", "Set Custom Class Names", "Legacy class-name Cbuf stream", ctx -> GameActions.bo2CalzoneClasses(ctx.service())));
        buttons.add(b("Recovery", "Guns And Camos", "Embedded unlock/camo payloads", ctx -> PayloadActions.bo2GunsAndCamos(ctx.service())));
        buttons.add(b("Recovery", "Unlock All", "Embedded unlock payload plus itemstat Cbufs", ctx -> PayloadActions.bo2UnlockAllAndStickStats(ctx.service())));
        buttons.add(b("Recovery", "True Unlock All", "Full ported unlock sequence", ctx -> PayloadActions.bo2TrueUnlockAll(ctx.service())));
        buttons.add(b("Recovery", "Stick Stats", "Legacy uploadstats/classset command stream", ctx -> GameActions.bo2StickStats(ctx.service())));

        List<GameToggle> toggles = List.of(
                t("Patches", "No Recoil", "NOP at 0x82259BC8",
                        ctx -> nop(ctx.service(), 0x82259BC8L),
                        ctx -> bytes(ctx.service(), 0x82259BC8L, 0x48, 0x46, 0x13, 0x41)),
                t("Patches", "ESP", "Five-address patch set",
                        ctx -> {
                            bytes(ctx.service(), 0x821C42F8L, 0x39, 0x40, 0xFF, 0xFF);
                            bytes(ctx.service(), 0x821C4CDCL, 0x3A, 0xE0, 0, 2);
                            bytes(ctx.service(), 0x821C44A0L, 0x3A, 0xE0, 0, 2);
                            bytes(ctx.service(), 0x821C47ECL, 0x40, 0x9A, 0, 0x18);
                            bytes(ctx.service(), 0x821C42FCL, 0x40, 0x9A, 0, 0x10);
                        },
                        ctx -> {
                            bytes(ctx.service(), 0x821C42F8L, 0x7F, 30, 80, 0);
                            bytes(ctx.service(), 0x821C4CDCL, 0x7E, 0xE8, 80, 0x2E);
                            bytes(ctx.service(), 0x821C44A0L, 0x7C, 0xE9, 0x40, 0x2E);
                            bytes(ctx.service(), 0x821C47ECL, 0x41, 0x9A, 0, 0x18);
                            bytes(ctx.service(), 0x821C42FCL, 0x40, 0x98, 0, 0x10);
                        }),
                t("Patches", "No Sway", "NOP at 0x826C6E6C",
                        ctx -> nop(ctx.service(), 0x826C6E6CL),
                        ctx -> bytes(ctx.service(), 0x826C6E6CL, 0x4B, 0xFF, 0xE9, 0x75)),
                t("Patches", "Red Boxes", "Writes 1/0 at 0x821F5B7F",
                        ctx -> ctx.service().writeByte(0x821F5B7FL, 1),
                        ctx -> ctx.service().writeByte(0x821F5B7FL, 0)),
                t("Patches", "Chams", "Writes 1/0 at 0x826B7687",
                        ctx -> ctx.service().writeByte(0x826B7687L, 1),
                        ctx -> ctx.service().writeByte(0x826B7687L, 0)),
                t("Patches", "Full Auto", "Patches 0x82255E1C",
                        ctx -> bytes(ctx.service(), 0x82255E1CL, 0x2B, 0x0B, 0, 1),
                        ctx -> bytes(ctx.service(), 0x82255E1CL, 0x2B, 0x0B, 0, 0)),
                t("Patches", "Laser", "Writes bool at 0x821C5567",
                        ctx -> ctx.service().writeBool(0x821C5567L, true),
                        ctx -> ctx.service().writeBool(0x821C5567L, false)),
                t("Patches", "VSAT", "Writes 1/0 at 0x821B8FD3",
                        ctx -> ctx.service().writeByte(0x821B8FD3L, 1),
                        ctx -> ctx.service().writeMemory(0x821B8FD3L, new byte[1])),
                t("Patches", "FPS Counter", "Patches 0x821FC04C",
                        ctx -> bytes(ctx.service(), 0x821FC04CL, 0x38, 0xC0, 0xFF, 0xFF),
                        ctx -> bytes(ctx.service(), 0x821FC04CL, 0x7F, 0xA6, 0xEB, 120)),
                t("Patches", "Invisible Gun", "Disables with the same NOP bytes",
                        ctx -> {
                            nop(ctx.service(), 0x82497EB0L);
                            GameActions.cbuf(ctx, "cg_gun_x -50");
                        },
                        ctx -> {
                            nop(ctx.service(), 0x82497EB0L);
                            GameActions.cbuf(ctx, "cg_gun_x 0");
                        })
        );

        return new GameDefinition(
                "Black Ops II",
                "0x415608C3",
                0x824015E0L,
                null,
                pairs(
                        "Aftermath", "mp_aftermath", "Cargo", "mp_dockside", "Carrier", "mp_carrier", "Drone", "mp_drone",
                        "Express", "mp_express", "Hijacked", "mp_hijacked", "Meltdown", "mp_meltdown", "Nuketown", "mp_nuketown_2020",
                        "Overflow", "mp_overflow", "Plaza", "mp_nightclub", "Raid", "mp_raid", "Slums", "mp_slums",
                        "Standoff", "mp_village", "Turbine", "mp_turbine", "Yemen", "mp_socotra", "Cove", "mp_Cove",
                        "Dig", "mp_dig", "Downhill", "mp_downhill", "Detour", "mp_Detour", "Encore", "mp_concert",
                        "Frost", "mp_frostbite", "Grind", "mp_skate", "Hydro", "mp_hydro", "Magma", "mp_magma",
                        "Mirage", "mp_mirage", "Pod", "mp_pod", "Rush", "mp_rush", "Studio", "mp_studio",
                        "Takeoff", "mp_takeoff", "Uplink", "mp_uplink", "Vertigo", "mp_vertigo"),
                Map.of(),
                buttons,
                toggles,
                statFields("prestige", "Prestige", "15", "level", "Level", "55", "tokens", "Tokens", "69", "kills", "Kills", "153176", "deaths", "Deaths", "92415", "wins", "Wins", "2341", "losses", "Losses", "1337", "headshots", "Headshots", "7521", "days", "Days", "69", "hours", "Hours", "0", "minutes", "Minutes", "0"),
                List.of(preset("Zero", "prestige", "0", "level", "0", "tokens", "0", "kills", "0", "deaths", "0", "wins", "0", "losses", "0", "headshots", "0", "days", "0", "hours", "0", "minutes", "0")),
                GameActions::bo2ApplyStats);
    }

    private static GameDefinition waw() {
        List<GameButton> buttons = new ArrayList<>();
        buttons.add(b("Lobby", "Force Start", "Starts the current lobby", cbuf("set party_minplayers 1;xpartygo")));
        buttons.add(b("Lobby", "Fast Restart", "Restarts the current match", cbuf("fast_restart")));
        buttons.add(b("Lobby", "End Game", "Ends the current match", GameActions::wawEndGame));
        buttons.add(b("Lobby", "Leave Game", "Disconnects from the current match", cbuf("disconnect")));
        buttons.add(b("Lobby", "Set Map", "Applies selected map", GameActions::setMap));
        buttons.add(b("Lobby", "Set Mode", "Applies selected mode", GameActions::setGametype));
        buttons.add(b("Lobby", "Switch Team", "Changes team from the Team field", GameActions::wawSwitchTeam));
        buttons.add(b("Commands", "Send Game Command", "Runs the game command field", GameActions::rawCbuf));
        buttons.add(b("Commands", "Send Server Command", "Sends the server command field", GameActions::wawSendServerCommand));
        buttons.add(b("Messages", "Say Message", "Sends chat message", GameActions::wawSayMessage));
        buttons.add(b("Messages", "Center Message", "Sends center-screen message", GameActions::wawCenterMessage));
        buttons.add(b("Messages", "Killfeed Message", "Sends killfeed message", GameActions::wawKillfeedMessage));
        buttons.add(b("Player", "Set FOV", "Changes local field of view", GameActions::setFov));
        buttons.add(b("Player", "Toggle God Mode", "Toggles god mode", cbuf("god")));
        buttons.add(b("Player", "Toggle Demigod", "Toggles demigod mode", cbuf("demigod")));
        buttons.add(b("Player", "Toggle No Clip", "Toggles no-clip movement", cbuf("noclip")));
        buttons.add(b("Player", "Toggle UFO", "Toggles UFO movement", cbuf("ufo")));
        buttons.add(b("Player", "Give All", "Gives all weapons/items", cbuf("give all")));
        buttons.add(b("Player", "Give Ammo", "Gives ammunition", cbuf("give ammo")));
        buttons.add(b("Player", "Set Local Gamertag", "Updates the pregame gamertag", ctx -> GameActions.wawLocalGamertag(ctx.service(), ctx.input("gamertag"))));
        buttons.add(b("Player", "Flash Gamertag", "Cycles a colored pregame gamertag", ctx -> GameActions.wawFlashGamertag(ctx.service(), ctx.input("gamertag"))));
        buttons.add(b("Clients", "Set Client Gamertag", "Updates selected client gamertag", GameActions::wawClientGamertag));
        buttons.add(b("Clients", "Give Client God Mode", "Enables god mode for selected clients", GameActions::wawClientGodMode));
        buttons.add(b("Clients", "Give Client UAV", "Enables UAV for selected clients", GameActions::wawClientUav));
        buttons.add(b("Clients", "Fake Max Rank", "Shows max rank for selected clients", GameActions::wawFakeMaxRank));
        buttons.add(b("Clients", "Fake Derank", "Shows deranked stats for selected clients", GameActions::wawFakeDerank));
        buttons.add(b("Recovery", "Unlock All", "Applies the unlock sequence", ctx -> GameActions.wawUnlockAll(ctx.service())));
        buttons.add(b("Recovery", "Apply Stats", "Writes recovery stats", ctx -> GameActions.wawApplyStats(ctx.service(), ctx)));
        buttons.add(b("Recovery", "Apply Selected Client Stats", "Writes stats to selected client", GameActions::wawApplySelectedClientStats));
        buttons.add(b("Recovery", "Reset Stats", "Resets local stats", ctx -> GameActions.wawResetStats(ctx.service())));
        buttons.add(b("Recovery", "Rainbow Class Names", "Sets colored class names", ctx -> GameActions.wawRainbowClassNames(ctx.service())));
        buttons.add(b("Zombies Commands", "Send Zombies Command", "Runs the zombies command field", GameActions::wawSendZombiesCommand));
        buttons.add(b("Zombies Commands", "Zombies Fast Restart", "Restarts zombies match", ctx -> GameActions.wawZombiesCbuf(ctx, "fast_restart")));
        buttons.add(b("Zombies Commands", "Zombies Give All", "Gives all zombies items", ctx -> GameActions.wawZombiesCbuf(ctx, "give all")));
        buttons.add(b("Zombies Commands", "Zombies Give Ammo", "Gives zombies ammunition", ctx -> GameActions.wawZombiesCbuf(ctx, "give ammo")));
        buttons.add(b("Zombies Commands", "Zombies Take All", "Removes all zombies weapons", ctx -> GameActions.wawZombiesCbuf(ctx, "take all")));
        buttons.add(b("Zombies Commands", "Zombies No Clip", "Toggles zombies no-clip movement", ctx -> GameActions.wawZombiesCbuf(ctx, "noclip")));
        buttons.add(b("Zombies Commands", "Zombies UFO", "Toggles zombies UFO movement", ctx -> GameActions.wawZombiesCbuf(ctx, "ufo")));
        buttons.add(b("Zombies Commands", "Zombies God Mode", "Toggles zombies god mode", ctx -> GameActions.wawZombiesCbuf(ctx, "god")));

        List<GameToggle> toggles = List.of(
                t("Lobby Patches", "Force Host", "Applies force-host dvars",
                        cbuf("party_connectToOthers 00;partyMigrate_disabled 01;sv_endGameIfISuck 0;badhost_endgameifisuck 0;set allowAllNAT 1"),
                        cbuf("party_connectToOthers 01;partyMigrate_disabled 00")),
                t("Player Patches", "Laser", "Toggles laser sight",
                        cbuf("cg_laserforceon 1"),
                        cbuf("cg_laserforceon 0")),
                t("Player Patches", "UAV", "Toggles enemy radar",
                        cbuf("compassEnemyFootstepEnabled 1;compassRadarUpdateTime 0.001;compass 0;g_compassshowenemies 1;compassEnemyFootstepMaxRange 99999;compassEnemyFootstepMaxZ 99999"),
                        cbuf("compassEnemyFootstepEnabled 0;compassRadarUpdateTime 0.001;compass 1;g_compassshowenemies 0;compassEnemyFootstepMaxRange 0;compassEnemyFootstepMaxZ 0")),
                t("Player Patches", "Wallhack", "Toggles near-plane wallhack",
                        cbuf("r_znear 45"),
                        cbuf("r_znear 1")),
                t("Player Patches", "No Recoil", "Toggles no recoil",
                        ctx -> nop(ctx.service(), 0x821CC47CL),
                        ctx -> bytes(ctx.service(), 0x821CC47CL, 0x4B, 0xFA, 0x5E, 0x4D)),
                t("Player Patches", "Chams", "Toggles chams",
                        ctx -> ctx.service().writeByte(0x821A6DEBL, 18),
                        ctx -> ctx.service().writeByte(0x821A6DEBL, 4)),
                t("Player Patches", "Unlimited Ammo", "Toggles sustained ammo",
                        cbuf("player_sustainAmmo 1"),
                        cbuf("player_sustainAmmo 0")),
                t("Player Patches", "Super Speed", "Toggles movement speed",
                        cbuf("g_speed 650"),
                        cbuf("g_speed 165")),
                t("Player Patches", "Low Gravity", "Toggles gravity",
                        cbuf("g_gravity 200"),
                        cbuf("g_gravity 800")),
                t("Player Patches", "Super Jump", "Toggles jump height",
                        ctx -> GameActions.wawSetJumpAndGravity(ctx.service(), true),
                        ctx -> GameActions.wawSetJumpAndGravity(ctx.service(), false)),
                t("Player Patches", "No Sway", "Toggles weapon sway",
                        ctx -> GameActions.wawNoSway(ctx.service(), true),
                        ctx -> GameActions.wawNoSway(ctx.service(), false)),
                t("Vision", "Chrome Vision", "Toggles chrome vision",
                        cbuf("r_specularmap 2"),
                        cbuf("r_specularmap 1")),
                t("Vision", "PC Vision", "Toggles PC vision",
                        cbuf("scr_art_tweak 1;scr_art_tweak_message 1;r_glowUseTweaks 1;r_filmUseTweaks 1"),
                        cbuf("scr_art_tweak 0;scr_art_tweak_message 0;r_glowUseTweaks 0;r_filmUseTweaks 0")),
                t("Vision", "Cartoon Vision", "Toggles fullbright vision",
                        cbuf("r_fullbright 1"),
                        cbuf("r_fullbright 0")),
                t("Vision", "Rainbow Vision", "Toggles rainbow shader",
                        cbuf("r_debugShader 1"),
                        cbuf("r_debugShader 0")),
                t("Vision", "Thermal Vision", "Toggles thermal vision",
                        cbuf("r_filmTweakInvert 1;r_filmusetweaks 1;r_filmtweakenable 1;r_filmTweakLightTint 5.3 6.3 7.2"),
                        cbuf("r_filmTweakInvert 0;r_filmusetweaks 0;r_filmtweakenable 0;r_filmTweakLightTint 0")),
                t("Vision", "Blue Vision", "Toggles blue vision",
                        cbuf("r_filmTweakInvert 1;r_filmTweakbrightness 2;r_filmusetweaks 1;r_filmTweakenable 1 0;toggle r_filmtweakLighttint 1.06 0.5 1.3"),
                        cbuf("r_filmTweakInvert 0;r_filmTweakbrightness 0;r_filmusetweaks 0;r_filmTweakenable 0;toggle r_filmtweakLighttint 0")),
                t("Vision", "Purple Vision", "Toggles purple vision",
                        cbuf("r_filmTweakInvert 1;set r_filmTweakbrightness 2;set r_filmusetweaks 1;set r_filmTweakenable 1;set r_filmtweakLighttint 1 2 1 1.1;set r_filmtweakdarktint 1 2 1"),
                        cbuf("r_filmTweakInvert 0;set r_filmTweakbrightness 0;set r_filmusetweaks 0;set r_filmTweakenable 0;set r_filmtweakLighttint 0;set r_filmtweakdarktint 0")),
                t("Vision", "No Fog", "Toggles fog",
                        cbuf("r_fog 0;scr_fog_disable 1"),
                        cbuf("r_fog 1;scr_fog_disable 0")),
                t("Zombies Patches", "Zombies No Recoil", "Toggles zombies no recoil",
                        ctx -> nop(ctx.service(), 0x821773D4L),
                        ctx -> bytes(ctx.service(), 0x821773D4L, 0x4B, 0xFB, 0x67, 0x6D)),
                t("Zombies Patches", "Zombies Chams", "Toggles zombies chams",
                        ctx -> bytes(ctx.service(), 0x82133030L, 0x38, 0xC0, 0x00, 0x12),
                        ctx -> bytes(ctx.service(), 0x82133030L, 0x60, 0x66, 0x00, 0x04)),
                t("Zombies Patches", "Zombies Full Auto", "Toggles zombies full-auto",
                        ctx -> ctx.service().writeByte(0x8212CB3BL, 0),
                        ctx -> ctx.service().writeByte(0x8212CB3BL, 1))
        );

        return new GameDefinition(
                "World at War",
                "0x4156081C",
                0x822A4AC8L,
                new ClientLayout(0x82C78770L, 816, 388, 15032, 18),
                pairs(
                        "Airfield", "mp_airfield", "Asylum", "mp_asylum", "Banzai", "mp_kwai",
                        "Breach", "mp_bgate", "Castle", "mp_castle", "Cliffside", "mp_shrine",
                        "Corrosion", "mp_stalingrad", "Courtyard", "mp_courtyard", "Dome", "mp_dome",
                        "Downfall", "mp_downfall", "Hangar", "mp_hangar", "Knee Deep", "mp_kneedeep",
                        "Makin", "mp_makin", "Makin Day", "mp_makin_day", "Nightfire", "mp_nachtfeuer",
                        "Outskirts", "mp_outskirts", "Roundhouse", "mp_roundhouse", "Seelow", "mp_seelow",
                        "Station", "mp_subway", "Sub Pens", "mp_docks", "Upheaval", "mp_suburban"),
                pairs("Capture the Flag", "ctf", "Domination", "dom", "Free for All", "dm", "Headquarters", "koth", "Sabotage", "sab", "Search and Destroy", "sd", "Team Deathmatch", "tdm", "War", "twar"),
                buttons,
                toggles,
                statFields("prestige", "Prestige", "10", "xp", "XP", "625919", "score", "Score", "550000", "kills", "Kills", "50000", "deaths", "Deaths", "25000", "wins", "Wins", "1500", "losses", "Losses", "10", "bestStreak", "Best Streak", "40", "headshots", "Headshots", "1800", "days", "Days", "8", "hours", "Hours", "3", "minutes", "Minutes", "1"),
                List.of(preset("Zero", "prestige", "0", "xp", "0", "score", "0", "kills", "0", "deaths", "0", "wins", "0", "losses", "0", "bestStreak", "0", "headshots", "0", "days", "0", "hours", "0", "minutes", "0")),
                GameActions::wawApplyStats);
    }

    private static GameDefinition bo3() {
        return limited("Black Ops III", "0x4156091D");
    }

    private static GameDefinition ghosts() {
        return limited("Ghosts", "0x415608FC");
    }

    private static GameDefinition advancedWarfare() {
        return limited("Advanced Warfare", "0x41560914");
    }

    private static GameDefinition limited(String name, String titleId) {
        return new GameDefinition(name, titleId, 0, null, Map.of(), Map.of(), List.of(), List.of(), List.of(), List.of(), null);
    }

    private static GameButton b(String group, String label, String detail, ActionRunner runner) {
        return new GameButton(group, label, detail, runner);
    }

    private static GameToggle t(String group, String label, String detail, ActionRunner enable, ActionRunner disable) {
        return new GameToggle(group, label, detail, enable, disable);
    }

    private static ActionRunner cbuf(String command) {
        return ctx -> GameActions.cbuf(ctx, command);
    }

    private static Map<String, String> pairs(String... data) {
        LinkedHashMap<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < data.length; i += 2) map.put(data[i], data[i + 1]);
        return map;
    }

    private static List<StatField> statFields(String... data) {
        List<StatField> fields = new ArrayList<>();
        for (int i = 0; i < data.length; i += 3) fields.add(new StatField(data[i], data[i + 1], data[i + 2]));
        return fields;
    }

    private static StatPreset preset(String name, String... data) {
        return new StatPreset(name, pairs(data));
    }

    private static void nop(ConsoleService service, long address) {
        service.writeUInt32BE(address, 0x60000000L);
    }

    private static void u32(ConsoleService service, long address, long value) {
        service.writeUInt32BE(address, value);
    }

    private static void bytes(ConsoleService service, long address, int... data) {
        service.writeMemory(address, HexUtils.bytes(data));
    }

    private static void fixedAscii(ConsoleService service, long address, String text, int length) {
        service.writeMemory(address, new byte[length]);
        service.writeFixedAscii(address, text, length);
    }

    private static void eachClient(ActionContext ctx, ClientWriter writer) throws Exception {
        if (ctx.game().clients() == null) throw new IllegalStateException("This game does not have a ported client layout");
        if (ctx.allClients()) {
            for (int i = 0; i < ctx.game().clients().maxClients(); i++) writer.accept(i);
        } else {
            writer.accept(ctx.clientIndex());
        }
    }

    @FunctionalInterface
    private interface ClientWriter {
        void accept(int index) throws Exception;
    }
}
