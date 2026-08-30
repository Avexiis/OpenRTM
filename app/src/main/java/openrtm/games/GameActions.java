package openrtm.games;

import com.jjrpc.JRPC;
import openrtm.console.ConsoleService;
import openrtm.util.HexUtils;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class GameActions {
    private GameActions() {
    }

    public static List<String> readClients(ConsoleService service, ClientLayout layout) {
        return java.util.stream.IntStream.range(0, layout.maxClients())
                .mapToObj(i -> {
                    try {
                        long playerState = service.readUInt32(layout.entityBase() + (long) i * layout.entitySize() + layout.playerStateOffset());
                        String name = service.readString(playerState + layout.nameOffset(), 0x40);
                        return String.format("%02d  %s", i, name.isBlank() ? "<empty>" : name);
                    } catch (Throwable t) {
                        return String.format("%02d  error: %s", i, t.getMessage());
                    }
                })
                .toList();
    }

    public static void cbuf(ActionContext ctx, String command) {
        ctx.service().cbuf(ctx.game().cbufAddress(), command);
    }

    public static void setMap(ActionContext ctx) {
        cbuf(ctx, "ui_mapname " + ctx.mapCode());
    }

    public static void setGametype(ActionContext ctx) {
        cbuf(ctx, "ui_gametype " + ctx.gametypeCode());
    }

    public static void setFov(ActionContext ctx) {
        cbuf(ctx, "cg_fov " + ctx.input("fov"));
    }

    public static void rawCbuf(ActionContext ctx) {
        cbuf(ctx, ctx.input("raw"));
    }

    public static void xamSpoof(ActionContext ctx) {
        ctx.service().xamSpoofGamertag(ctx.input("gamertag"));
    }

    public static void cod4EndGame(ActionContext ctx) {
        long serverId = ctx.service().titleCallLong(0x821D1570L, "sv_serverid");
        cbuf(ctx, "cmd mr " + serverId + " -1 endround");
    }

    public static void cod4SwitchTeam(ActionContext ctx) {
        long serverId = ctx.service().titleCallLong(0x821D1570L, "sv_serverid");
        cbuf(ctx, "cmd mr " + serverId + " 4 " + ctx.input("team"));
    }

    public static void cod4Motd(ActionContext ctx) {
        cbuf(ctx, "motd " + ctx.input("message") + "^2 - Powered by calzones ;) ");
        cbuf(ctx, "updategamerprofile");
    }

    public static void cod4InGameGamertag(ActionContext ctx) {
        ctx.service().writeAsciiNull(0x84C24BBCL, ctx.input("gamertag"));
    }

    public static void cod4OffhostGamertag(ActionContext ctx) {
        cbuf(ctx, "userinfo \\name\\" + ctx.input("gamertag"));
    }

    public static void cod4RankedPrivateMatch(ActionContext ctx) throws InterruptedException {
        cbuf(ctx, "developer 1;developer_script 1");
        Thread.sleep(100);
        cbuf(ctx, "set scr_game_suicidepointloss 1;set scr_dm_score_suicide 4000");
        cbuf(ctx, "onlinegame 1;xblive_privatematch 0;scr_dm_timelimit 0.1");
        cbuf(ctx, "set activeaction\"scr_dm_timelimit 0;scr_dm_scorelimit 0\"");
    }

    public static void cod4SetClientStat(ConsoleService service, int clientIndex, int statIndex, int value) {
        service.callVoid(0x82205C38L, clientIndex, statIndex, value);
    }

    public static void cod4ApplyStats(ConsoleService service, ActionContext ctx) {
        int xp = cod4Xp(ctx.intInput("prestige"));
        int assists = ctx.intInput("assists");
        int kills = ctx.intInput("kills");
        int wins = ctx.intInput("wins");
        int killstreak = ctx.intInput("killstreak");
        int score = ctx.intInput("score");
        int winstreak = ctx.intInput("winstreak");
        int hits = ctx.intInput("hits");
        int misses = ctx.intInput("misses");
        int losses = ctx.intInput("losses");
        int headshots = ctx.intInput("headshots");
        int deaths = ctx.intInput("deaths");
        int prestige = ctx.intInput("prestige");
        int rank = ctx.intInput("rank");
        int time = ctx.intInput("time");

        if (!ctx.allClients()) {
            service.writeUInt32BE(0x84C5EEC0L, xp);
            service.writeUInt32BE(0x84C5EEFCL, wins);
            service.writeUInt32BE(0x84C5EECCL, killstreak);
            service.writeUInt32BE(0x84C5EEC4L, score);
            service.writeUInt32BE(0x84C5EF08L, winstreak);
            service.writeUInt32BE(0x84C5EF14L, hits);
            service.writeUInt32BE(0x84C5EF18L, misses);
            service.writeUInt32BE(0x84C5EF00L, losses);
            service.writeUInt32BE(0x84C5EEDCL, headshots);
            service.writeUInt32BE(0x84C5EED0L, deaths);
            service.writeUInt32BE(0x84C5EED0L, assists);
            service.writeUInt32BE(0x84C5EEF4L, time);
            service.writeUInt32BE(0x84C5EF24L, prestige);
        } else {
            int client = ctx.clientIndex();
            cod4SetClientStat(service, client, 0x8FD, xp);
            cod4SetClientStat(service, client, 0x901, rank);
            cod4SetClientStat(service, client, 0x903, assists);
            cod4SetClientStat(service, client, 0x8FF, kills);
            cod4SetClientStat(service, client, 0x90C, wins);
            cod4SetClientStat(service, client, 0x900, killstreak);
            cod4SetClientStat(service, client, 0x8FE, score);
            cod4SetClientStat(service, client, 0x910, winstreak);
            cod4SetClientStat(service, client, 0x912, hits);
            cod4SetClientStat(service, client, 0x913, misses);
            cod4SetClientStat(service, client, 0x90D, losses);
            cod4SetClientStat(service, client, 0x904, headshots);
            cod4SetClientStat(service, client, 0x901, deaths);
            cod4SetClientStat(service, client, 0x916, prestige);
            cod4SetClientStat(service, client, 0x90A, time);
        }
    }

    public static int cod4Xp(int rank) {
        return switch (rank) {
            case 1 -> 0; case 2 -> 30; case 3 -> 120; case 4 -> 270; case 5 -> 480; case 6 -> 750;
            case 7 -> 1080; case 8 -> 1470; case 9 -> 1920; case 10 -> 2430; case 11 -> 3000; case 12 -> 3650;
            case 13 -> 4380; case 14 -> 5190; case 15 -> 6080; case 16 -> 7050; case 17 -> 8120; case 18 -> 9290;
            case 19 -> 10560; case 20 -> 11930; case 21 -> 13440; case 22 -> 15100; case 23 -> 16910; case 24 -> 18880;
            case 25 -> 21000; case 26 -> 23290; case 27 -> 25740; case 28 -> 28360; case 29 -> 31150; case 30 -> 34120;
            case 31 -> 37280; case 32 -> 40640; case 33 -> 44200; case 34 -> 47980; case 35 -> 52000; case 36 -> 56280;
            case 37 -> 60840; case 38 -> 65710; case 39 -> 70920; case 40 -> 76500; case 41 -> 82480; case 42 -> 88890;
            case 43 -> 95760; case 44 -> 103120; case 45 -> 111000; case 46 -> 119430; case 47 -> 128440; case 48 -> 138060;
            case 49 -> 148320; case 50 -> 159250; case 51 -> 170880; case 52 -> 183240; case 53 -> 196360; case 54 -> 210270;
            case 55 -> 125080;
            default -> 0;
        };
    }

    public static void mw2EndGame(ActionContext ctx) {
        int serverId = ctx.service().readInt32(0x826237E0L);
        ctx.service().callVoid(0x822CC810L, 0, "cmd mr " + serverId + " -1 endround");
    }

    public static void mw2DisableGame(ConsoleService service) {
        service.writeMemory(0x822CC830L, HexUtils.bytes(0x48, 0x00, 0x00, 0x0C));
    }

    public static void mw2NoFlash(ConsoleService service, boolean enable) {
        long[] bases = {0x825556F4L, 0x8255595CL};
        int[][] onValues = new int[][] {
                {0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000},
                {0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000, 0x00000000}
        };
        int[][] offValues = new int[][] {
                {0x00000000, 0x000003E8, 0x00000190, 0x000DAC00, 0x00000000, 0x00000000, 0x00000BB8, 0x3AAEC33F, 0x01, 0x01000000, 0x000007D0, 0x3F000000, 0x42340000, 0x42340000, 0x000000FA, 0x000009C4},
                {0x00000000, 0x0003E800, 0x00019000, 0x000DAC00, 0x0003E800, 0x00000100, 0x000BB83A, 0xAEC33F3D, 0x01, 0x01000000, 0x000007D0, 0x3F000000, 0x42B40000, 0x42B40000, 0x000000FA, 0x000007D0}
        };
        int[] offsets = {0x0, 0x4, 0x8, 0xC, 0x10, 0x14, 0x18, 0x1C, 0x23, 0x254, 0x258, 0x25C, 0x260, 0x264, 0x128, 0x12C};
        int[][] selected = enable ? onValues : offValues;
        for (int i = 0; i < bases.length; i++) {
            for (int q = 0; q < offsets.length; q++) {
                service.writeUInt32BE(bases[i] + offsets[q], selected[i][q]);
            }
        }
    }

    public static void mw2ServerCommand(ConsoleService service, int clientIndex, String command) {
        service.titleCallVoid(0x82254940L, clientIndex, -1, command);
    }

    public static void mw2UnlockAll(ConsoleService service) {
        mw2ServerCommand(service, -1, "s loc_warnings 0");
        mw2ServerCommand(service, -1, "s loc_warningsUI 0");
        mw2ServerCommand(service, -1, "c \"^5Big Boy ^3Starting ^6Unlock ^3All...\"");
        mw2ServerCommand(service, -1, mw2ChallengeCommand(3500, 3550, true));
        for (int start = 3550; start < 5000; start += 50) {
            if (start == 3850) mw2ServerCommand(service, -1, "c \"^525 Percent ^6Unlocked\"");
            if (start == 4350) mw2ServerCommand(service, -1, "c \"^550 Percent ^6Unlocked\"");
            if (start == 4650) mw2ServerCommand(service, -1, "c \"^575 Percent ^6Unlocked\"");
            mw2ServerCommand(service, -1, mw2ChallengeCommand(start, Math.min(start + 50, 5000), false));
        }
        mw2ServerCommand(service, -1, "c \"^5Big Boy ^3Unlocked All ^6Challenges!!\"");
    }

    private static String mw2ChallengeCommand(int start, int end, boolean first) {
        StringBuilder sb = new StringBuilder("J ");
        if (first) sb.append("6525 7F ");
        for (int i = start; i <= end; i++) {
            if (i > start) sb.append(' ');
            sb.append(i).append(" 99");
        }
        return sb.toString();
    }

    public static int mw2Xp(int rank) {
        return switch (rank) {
            case 1 -> 0; case 2 -> 500; case 3 -> 0x6A4; case 4 -> 0xE10; case 5 -> 0x1838; case 6 -> 0x251C;
            case 7 -> 0x34BC; case 8 -> 0x4718; case 9 -> 0x5C30; case 10 -> 0x7404; case 11 -> 0x8E94; case 12 -> 0xAD0C;
            case 13 -> 0xCF6C; case 14 -> 0xF5B4; case 15 -> 0x11FE4; case 16 -> 0x14DFC; case 17 -> 0x1782C; case 18 -> 0x1B5E4;
            case 19 -> 0x1EFB4; case 20 -> 0x22D6C; case 21 -> 0x26F0C; case 22 -> 0x2B494; case 23 -> 0x2FE04; case 24 -> 0x34B5C;
            case 25 -> 0x39C9C; case 26 -> 0x3F1C4; case 27 -> 0x44AD4; case 28 -> 0x4A7CC; case 29 -> 0x508AC; case 30 -> 0x56D74;
            case 31 -> 0x5D6EC; case 32 -> 0x64514; case 33 -> 0x6B7EC; case 34 -> 0x72F74; case 35 -> 0x7ABAC; case 36 -> 0x82C94;
            case 37 -> 0x8B22C; case 38 -> 0x93C74; case 39 -> 0x9CB6C; case 40 -> 0xA5F14; case 41 -> 0xAF76C; case 42 -> 0xB9474;
            case 43 -> 0xC362C; case 44 -> 0xCDC94; case 45 -> 0xD87AC; case 46 -> 0xE3774; case 47 -> 0xEEBEC; case 48 -> 0xFA514;
            case 49 -> 0x1062EC; case 50 -> 0x112574; case 51 -> 0x11EDD8; case 52 -> 0x12BC18; case 53 -> 0x139034; case 54 -> 0x146A2C;
            case 55 -> 0x154A00; case 56 -> 0x162FB0; case 57 -> 0x171B3C; case 58 -> 0x180CA4; case 59 -> 0x1903E8; case 60 -> 0x1A0108;
            case 61 -> 0x1B0404; case 62 -> 0x1C0CDC; case 63 -> 0x1D1B90; case 64 -> 0x1E3020; case 65 -> 0x1F4A8C; case 66 -> 0x206AD4;
            case 67 -> 0x2190F8; case 68 -> 0x22BDC0; case 69 -> 0x23EED4; case 70 -> 0x266420;
            default -> 0;
        };
    }

    public static void mw2ApplyRecoveryStats(ConsoleService service, ActionContext ctx) {
        service.writeMemory(0x831A0DCCL, HexUtils.int32Little(mw2Xp(ctx.intInput("rank"))));
        service.writeByte(0x831A0DD4L, ctx.intInput("prestige"));
        service.writeMemory(0x831A0DDCL, HexUtils.int32Little(ctx.intInput("score")));
    }

    public static void mw2Redboxes(ConsoleService service, ClientLayout layout, int clientIndex, boolean enable) {
        long playerState = service.readUInt32(layout.entityBase() + (long) clientIndex * layout.entitySize() + layout.playerStateOffset());
        service.writeByte(playerState + 0x13, enable ? 0x10 : 0);
    }

    public static void bo1EndGame(ActionContext ctx) {
        int serverId = ctx.service().readInt32(0x829BE624L);
        cbuf(ctx, "cmd mr " + serverId + " -1 endround");
    }

    public static void bo1InGameGamertag(ActionContext ctx) {
        cbuf(ctx, "userinfo \\name\\" + ctx.input("gamertag"));
    }

    public static void bo1ApplyStats(ConsoleService service, ActionContext ctx) {
        service.writeInt32LE(0x8408E805L, 0x1343A4);
        service.writeInt32LE(0x8408E7FDL, ctx.intInput("prestige"));
        service.writeInt32LE(0x8408E801L, ctx.intInput("rank"));
        service.writeInt32LE(0x8408E549L, ctx.intInput("kills"));
        service.writeInt32LE(0x8408E415L, ctx.intInput("deaths"));
        service.writeInt32LE(0x8408E819L, ctx.intInput("score"));
        service.writeInt32LE(0x820CDCE8L, ctx.intInput("headshots"));
        service.writeInt32LE(0x8408E87DL, ctx.intInput("wins"));
        service.writeInt32LE(0x8408E3B1L, ctx.intInput("losses"));
        service.writeInt32LE(0x8337B680L, ctx.intInput("assists"));
        service.writeInt32LE(0x8408E3F1L, ctx.intInput("codpoints"));
        service.cbuf(0x8233E8D8L, "uploadstats; updategamerprofile");
        service.xNotify("BO1\nStats Sent", JRPC.XNotiyLogo.FLASHING_XBOX_CONSOLE);
    }

    public static void bo1DumbStats(ConsoleService service) {
        byte[] zeros = new byte[255];
        service.writeMemory(0x8408E7FDL, zeros);
        service.writeInt32LE(0x8408E805L, 0x1343A4);
        service.writeMemory(0x8408E801L, zeros);
        service.writeMemory(0x8408E549L, zeros);
        service.writeMemory(0x8408E415L, zeros);
        service.writeMemory(0x8408E819L, zeros);
        service.writeMemory(0x820CDCE8L, zeros);
        service.writeMemory(0x8408E87DL, zeros);
        service.writeMemory(0x8408E3B1L, zeros);
        service.writeMemory(0x8337B680L, zeros);
        service.writeMemory(0x8408E3F1L, zeros);
    }

    public static void bo1UnlockAchievements(ConsoleService service) {
        service.cbuf(0x8233E8D8L, "^6Calzones Multi-Tool ^5Is Unlocking ^2Achievments ^1 < 3");
        for (int i = 0; i < BO1_ACHIEVEMENTS.length; i++) {
            service.callVoid(0x82364E18L, -1, -1, "8 " + BO1_ACHIEVEMENTS[i]);
            if (i == 8) service.cbuf(0x8233E8D8L, "^6Calzones Multi-Tool ^5Is Unlocking ^225%");
            if (i == 25) service.cbuf(0x8233E8D8L, "Calzones Multi-Tool ^5Is Unlocking ^250%");
            if (i == 45) service.cbuf(0x8233E8D8L, "Calzones Multi-Tool ^5Is Unlocking ^275%");
        }
        service.cbuf(0x8233E8D8L, "Calzones Multi-Tool ^5Is Finsished Unlocking Achievments! ^1 < 3");
    }

    private static final String[] BO1_ACHIEVEMENTS = {
            "SP_WIN_CUBA", "SP_WIN_VORKUTA", "SP_WIN_PENTAGON", "SP_WIN_FLASHPOINT", "SP_WIN_KHE_SANH", "SP_WIN_HUE_CITY",
            "SP_WIN_KOWLOON", "SP_WIN_RIVER", "SP_WIN_FULLAHEAD", "SP_WIN_INTERROGATION_ESCAPE", "SP_WIN_UNDERWATERBASE",
            "SP_VWIN_FLASHPOINT", "SP_VWIN_HUE_CITY", "SP_VWIN_RIVER", "SP_VWIN_FULLAHEAD", "SP_VWIN_UNDERWATERBASE",
            "SP_LVL_KHESANH_MISSILES", "SP_LVL_HUECITY_AIRSUPPORT", "SP_LVL_HUECITY_DRAGON", "SP_LVL_CREEK1_DESTROY_MG",
            "SP_LVL_CREEK1_KNIFING", "SP_LVL_KOWLOON_DUAL", "SP_LVL_RIVER_TARGETS", "SP_LVL_WMD_RSO", "SP_LVL_WMD_RELAY",
            "SP_LVL_POW_HIND", "SP_LVL_POW_FLAMETHROWER", "SP_LVL_FULLAHEAD_2MIN", "SP_LVL_REBIRTH_MONKEYS",
            "SP_LVL_REBIRTH_NOLEAKS", "SP_LVL_UNDERWATERBASE_MINI", "SP_LVL_FRONTEND_CHAIR", "SP_LVL_FRONTEND_ZORK",
            "SP_GEN_MASTER", "SP_GEN_FRAGMASTER", "SP_GEN_ROUGH_ECO", "SP_GEN_CROSSBOW", "SP_GEN_FOUNDFILMS",
            "SP_ZOM_COLLECTOR", "SP_ZOM_NODAMAGE", "SP_ZOM_TRAPS", "SP_ZOM_SILVERBACK", "SP_ZOM_CHICKENS",
            "SP_ZOM_FLAMINGBULL", "MP_FILM_CREATED", "MP_WAGER_MATCH", "MP_PLAY", "DLC1_ZOM_OLDTIMER",
            "DLC1_ZOM_HARDWAY", "DLC1_ZOM_PISTOLERO", "DLC1_ZOM_BIGBADDABOOM", "DLC1_ZOM_NOLEGS",
            "DLC2_ZOM_PROTECTEQUIP", "DLC2_ZOM_LUNARLANDERS", "DLC2_ZOM_FIREMONKEY", "DLC2_ZOM_BLACKHOLE",
            "DLC2_ZOM_PACKAPUNCH", "DLC3_ZOM_STUNTMAN", "DLC3_ZOM_SHOOTING_ON_LOCATION", "DLC3_ZOM_QUIET_ON_THE_SET",
            "DLC4_ZOM_TEMPLE_SIDEQUEST", "DLC5_ZOM_CRYOGENIC_PARTY", "DLC5_ZOM_BIG_BANG_THEORY",
            "DLC5_ZOM_GROUND_CONTROL", "DLC5_ZOM_ONE_SMALL_HACK", "DLC5_ZOM_PERKS_IN_SPACE", "DLC5_ZOM_FULLY_ARMED",
            "DLC4_ZOM_ZOMB_DISPOSAL", "DLC4_ZOM_MONKEY_SEE_MONKEY_DONT", "DLC4_ZOM_BLINDED_BY_THE_FRIGHT",
            "DLC4_ZOM_SMALL_CONSOLATION"
    };

    public static void bo1RandomizeCardBytes(ConsoleService service) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        byte[] two = new byte[2];
        random.nextBytes(two); service.writeMemory(0x84085AAAL, two);
        random.nextBytes(two); service.writeMemory(0x84085AB7L, two);
        random.nextBytes(two); service.writeMemory(0x84085AA6L, two);
        random.nextBytes(two); service.writeMemory(0x84085AB2L, two);
        byte[] one = new byte[1];
        random.nextBytes(one); service.writeMemory(0x84085AC3L, one);
    }

    public static void bo1RandomizeBackground(ConsoleService service) {
        byte[] data = new byte[26];
        ThreadLocalRandom.current().nextBytes(data);
        service.writeMemory(0x84085AC6L, data);
    }

    public static void mw3ApplyStats(ConsoleService service, ActionContext ctx) {
        service.writeInt32LE(0x830A6D6CL, ctx.intInput("prestige"));
        service.writeInt32LE(0x830A8BCBL, ctx.intInput("tokens"));
        service.writeUInt32BE(0x830A6B5CL, 0x1AA518);
        service.writeInt32LE(0x830A6DD0L, ctx.intInput("wins"));
        service.writeInt32LE(0x830A6DD4L, ctx.intInput("losses"));
        service.writeInt32LE(0x830A6DD8L, ctx.intInput("kills"));
        service.writeInt32LE(0x830A6D74L, ctx.intInput("score"));
        service.writeInt32LE(0x830A6DD8L, ctx.intInput("kills"));
        service.writeInt32LE(0x830A6DA4L, ctx.intInput("deaths"));
        service.writeInt32LE(0x830A6DACL, ctx.intInput("assists"));
        service.writeInt32LE(0x830A6DB0L, ctx.intInput("headshots"));
        service.writeInt32LE(0x830A6DA0L, ctx.intInput("killstreak"));
    }

    public static void mw3CustomClasses(ConsoleService service) {
        long[] addresses = {0x830A711CL, 0x830A717EL, 0x830A71E0L, 0x830A7242L, 0x830A72A4L, 0x830A7306L, 0x830A7368L, 0x830A73CAL, 0x830A742CL, 0x830A748EL};
        for (int i = 0; i < addresses.length; i++) {
            service.writeAsciiNull(addresses[i], "^1Custom Class " + (i + 1));
        }
    }

    public static void mw3ThreeDeeName(ConsoleService service, String first, String second) {
        byte[] data = (first + " " + second + "\0").getBytes(StandardCharsets.US_ASCII);
        if (first.length() < data.length) data[first.length()] = 0x0D;
        service.writeMemory(0x839691ACL, data);
    }

    public static void bo2SetGamertag(ConsoleService service, String gamertag) {
        service.writeMemory(0x82C55D00L, HexUtils.bytes(0x7C, 0x83, 0x23, 0x78, 0x3D, 0x60, 0x82, 0xC5, 0x38, 0x8B, 0x5D, 0x60, 0x3D, 0x60, 0x82, 0x4A, 0x39, 0x6B, 0xDC, 0xA0, 0x38, 0xA0, 0x00, 0x20, 0x7D, 0x69, 0x03, 0xA6, 0x4E, 0x80, 0x04, 0x20));
        service.writeMemory(0x8293D724L, HexUtils.bytes(0x3D, 0x60, 0x82, 0xC5, 0x39, 0x6B, 0x5D, 0x00, 0x7D, 0x69, 0x03, 0xA6, 0x4E, 0x80, 0x04, 0x20));
        service.writeMemory(0x8259B6A7L, new byte[1]);
        service.writeMemory(0x822D1110L, HexUtils.bytes(0x40));
        service.writeMemory(0x82C55D60L, HexUtils.asciiNull(gamertag));
    }

    public static void bo2CalzoneClasses(ConsoleService service) {
        service.writeMemory(0x843546B2L, HexUtils.bytes(0x44, 0x80, 0x08, 0x10, 0x01, 0x22, 0x40, 0x04));
        long cbuf = 0x824015E0L;
        String[] commands = {
                "setStatFromLocString cacloadouts customclassname 0 ^5Modded",
                "setStatFromLocString cacloadouts customclassname 1 ^6Classes",
                "setStatFromLocString cacloadouts customclassname 2 ^1Done",
                "setStatFromLocString cacloadouts customclassname 3 ^5With",
                "setStatFromLocString cacloadouts customclassname 4 ^6King",
                "setStatFromLocString cacloadouts customclassname 5 ^3Calzone's",
                "setStatFromLocString cacloadouts customclassname 6 ^1Multi-Tool",
                "setStatFromLocString cacloadouts customclassname 7 ^3'Big Boy'",
                "setStatFromLocString cacloadouts customclassname 8 ^5We Don't Pay",
                "setStatFromLocString cacloadouts customclassname 9 ^2For Shit Here",
                "setStatFromLocString custommatchcacloadouts customclassname 0 \"^1Classes\"",
                "setStatFromLocString custommatchcacloadouts customclassname 1 \"^2Done\"",
                "setStatFromLocString custommatchcacloadouts customclassname 2 \"^3With\"",
                "setStatFromLocString custommatchcacloadouts customclassname 3 \"^4Big Boy\"",
                "setStatFromLocString custommatchcacloadouts customclassname 4 \"^5Multi-Tool\"",
                "setStatFromLocString leaguecacloadouts customclassname 0 \"^1Classes\"",
                "setStatFromLocString leaguecacloadouts customclassname 1 \"^2Done\"",
                "setStatFromLocString leaguecacloadouts customclassname 2 \"^3With\"",
                "setStatFromLocString leaguecacloadouts customclassname 3 \"^4Big Boy\"",
                "setStatFromLocString leaguecacloadouts customclassname 4 \"^5Multi-Tool\"",
                "setPublicMatchClassSetNameFromLocString 0 \"^2Big Boy v1.0 <3\"",
                "setCustomMatchClassSetNameFromLocString 0 \"^2Big Boy v1.0 <3\"",
                "setLeagueMatchClassSetNameFromLocString 0 \"^2Big Boy v1.0 <3\"",
                "uploadstats; updategamerprofile"
        };
        for (String command : commands) service.cbuf(cbuf, command);
    }

    public static void bo2StickStats(ConsoleService service) {
        long cbuf = 0x824015E0L;
        service.cbuf(cbuf, "setPublicMatchClassSetNameFromLocString 0 \"^2Big Boy v1.0 <3\"");
        service.cbuf(cbuf, "setCustomMatchClassSetNameFromLocString 0 \"^2Big Boy v1.0 <3\"");
        service.cbuf(cbuf, "setLeaueMatchClassSetNameFromLocString 0 \"^2Big Boy v1.0 <3\"");
        service.cbuf(cbuf, "uploadstats; updategamerprofile");
    }

    public static void bo2ApplyStats(ConsoleService service, ActionContext ctx) {
        service.writeInt32LE(0x843491A4L, ctx.intInput("prestige"));
        service.writeInt32LE(0x843491BCL, 0x130F4C);
        service.writeInt32LE(0x84348D00L, ctx.intInput("headshots"));
        service.writeInt32LE(0x84348AD2L, ctx.intInput("deaths"));
        service.writeInt32LE(0x843492E2L, ctx.intInput("level"));
        service.writeInt32LE(0x84348D72L, ctx.intInput("losses"));
        service.writeInt32LE(0x843491BCL, 0x130F4C);
        service.writeInt32LE(0x843491E0L, ctx.intInput("wins"));
        service.writeInt32LE(0x843491B6L, ctx.intInput("tokens"));
        int totalSeconds = ctx.intInput("days") * 0x15180 + ctx.intInput("hours") * 0xE10 + ctx.intInput("minutes") * 60;
        service.writeInt32LE(0x8434929AL, totalSeconds);
        service.writeInt32LE(0x84348BD4L, ctx.intInput("kills"));
        service.writeInt32LE(0x8435292EL, ctx.intInput("days"));
    }

    public static void bo2PatchVersion(ConsoleService service) {
        service.writeUInt32BE(0x82273C88L, 0x60000000L);
        service.writeUInt32BE(0x822781D8L, 0x60000000L);
        service.writeUInt32BE(0x822A06B0L, 0x60000000L);
        service.writeUInt16BE(0x8227BBDCL, 0x4800);
    }

    public static void wawEndGame(ActionContext ctx) {
        long serverId = ctx.service().titleCallLong(0x822F28D8L, "sv_serverid");
        cbuf(ctx, "cmd mr " + serverId + " -1 endround");
    }

    public static void wawSwitchTeam(ActionContext ctx) {
        long serverId = ctx.service().titleCallLong(0x822F28D8L, "sv_serverid");
        cbuf(ctx, "cmd mr " + serverId + " 4 " + ctx.input("team"));
    }

    public static void wawSendServerCommand(ActionContext ctx) throws Exception {
        wawEachClient(ctx, client -> wawServerCommand(ctx.service(), client, 0, ctx.input("serverCommand")));
    }

    public static void wawCenterMessage(ActionContext ctx) throws Exception {
        wawEachClient(ctx, client -> wawServerCommand(ctx.service(), client, 0, "c \"" + ctx.input("message") + "\""));
    }

    public static void wawKillfeedMessage(ActionContext ctx) throws Exception {
        wawEachClient(ctx, client -> wawServerCommand(ctx.service(), client, 0, "f \"" + ctx.input("message") + "\""));
    }

    public static void wawSayMessage(ActionContext ctx) {
        cbuf(ctx, "say \"" + ctx.input("message") + "\"");
    }

    public static void wawUnlockAll(ConsoleService service) {
        service.cbuf(0x822A4AC8L, "exec mp/unlock_menu.cfg;exec mp/unlock_allperks.cfg;exec mp/unlock_allweapon.cfg;exec mp/unlock_challenges.cfg;exec mp/unlock_init.cfg;updategamerprofile;uploadstats");
    }

    public static void wawResetStats(ConsoleService service) {
        service.cbuf(0x822A4AC8L, "resetstats;updategamerprofile;uploadstats");
    }

    public static void wawApplyStats(ConsoleService service, ActionContext ctx) {
        int time = ctx.intInput("days") * 86_400 + ctx.intInput("hours") * 3_600 + ctx.intInput("minutes") * 60;
        if (ctx.allClients() && ctx.game().clients() != null) {
            for (int client = 0; client < ctx.game().clients().maxClients(); client++) {
                wawApplyClientStats(service, client, ctx, time);
            }
            return;
        }

        wawStatSet(service, 2326, ctx.intInput("prestige"));
        wawStatSet(service, 2301, ctx.intInput("xp"));
        wawStatSet(service, 2302, ctx.intInput("score"));
        wawStatSet(service, 2303, ctx.intInput("kills"));
        wawStatSet(service, 2304, ctx.intInput("bestStreak"));
        wawStatSet(service, 2305, ctx.intInput("deaths"));
        wawStatSet(service, 2308, ctx.intInput("headshots"));
        wawStatSet(service, 2311, time);
        wawStatSet(service, 2316, ctx.intInput("wins"));
        wawStatSet(service, 2317, ctx.intInput("losses"));
        service.cbuf(0x822A4AC8L, "updategamerprofile;uploadstats");
    }

    public static void wawApplySelectedClientStats(ActionContext ctx) {
        int time = ctx.intInput("days") * 86_400 + ctx.intInput("hours") * 3_600 + ctx.intInput("minutes") * 60;
        wawApplyClientStats(ctx.service(), ctx.clientIndex(), ctx, time);
    }

    public static void wawRainbowClassNames(ConsoleService service) throws InterruptedException {
        String gamertag = service.readCurrentGamertag();
        String[] names = {
                "^1" + gamertag, "^2" + gamertag, "^3" + gamertag, "^4" + gamertag, "^5" + gamertag,
                "^1" + gamertag, "^2" + gamertag, "^3" + gamertag, "^4" + gamertag, "^5" + gamertag
        };
        for (int i = 0; i < 5; i++) {
            service.cbuf(0x822A4AC8L, "customclass" + (i + 1) + " \"" + names[i] + "\"");
            Thread.sleep(30);
        }
        for (int i = 0; i < 5; i++) {
            service.cbuf(0x822A4AC8L, "prestigeclass" + (i + 1) + " \"" + names[i + 5] + "\"");
            Thread.sleep(30);
        }
        service.cbuf(0x822A4AC8L, "updategamerprofile;uploadstats");
    }

    public static void wawLocalGamertag(ConsoleService service, String gamertag) {
        service.writeAsciiNull(0x852FE0F5L, gamertag);
    }

    public static void wawFlashGamertag(ConsoleService service, String gamertag) {
        int color = ThreadLocalRandom.current().nextInt(0, 7);
        service.writeAsciiNull(0x852FE0F0L, "^" + color + gamertag);
    }

    public static void wawClientGamertag(ActionContext ctx) {
        long playerState = wawPlayerState(ctx.service(), ctx.clientIndex());
        ctx.service().writeAsciiNull(playerState + 15032L, ctx.input("gamertag"));
    }

    public static void wawClientGodMode(ActionContext ctx) throws Exception {
        wawEachClient(ctx, client -> {
            ctx.service().writeByte(wawEntity(client) + 439L, 1);
            wawServerCommand(ctx.service(), client, 1, "e \"God Mode [^2ON^7]\"");
        });
    }

    public static void wawClientUav(ActionContext ctx) throws Exception {
        wawEachClient(ctx, client -> {
            wawServerCommand(ctx.service(), client, 1, "v g_compassshowenemies 1;cg_drawshellshock 0;set activeaction");
            wawServerCommand(ctx.service(), client, 0, "c \"^2UAV Given\"");
        });
    }

    public static void wawFakeMaxRank(ActionContext ctx) throws Exception {
        wawEachClient(ctx, client -> {
            long playerState = 0x82DDAE18L + (long) client * 15468L;
            ctx.service().writeUInt32BE(playerState + 15075L, 10);
            ctx.service().writeUInt32BE(playerState + 15071L, 65);
        });
    }

    public static void wawFakeDerank(ActionContext ctx) throws Exception {
        wawEachClient(ctx, client -> {
            long playerState = 0x82DDAE18L + (long) client * 15468L;
            ctx.service().writeUInt32BE(playerState + 15075L, 1);
            ctx.service().writeUInt32BE(playerState + 15071L, 1);
        });
    }

    public static void wawSetJumpAndGravity(ConsoleService service, boolean enable) {
        if (enable) {
            service.cbuf(0x822A4AC8L, "jump_height 999;bg_fallDamageMinHeight 998;bg_fallDamageMaxHeight 999");
        } else {
            service.cbuf(0x822A4AC8L, "jump_height 39;bg_fallDamageMinHeight 128;bg_fallDamageMaxHeight 300");
        }
    }

    public static void wawNoSway(ConsoleService service, boolean enable) {
        if (enable) {
            service.cbuf(0x822A4AC8L, "player_breath_fire_delay 0;player_breath_gasp_lerp 0;player_breath_gasp_scale 0.0;player_breath_gasp_time 0;player_breath_snd_delay 0");
        } else {
            service.cbuf(0x822A4AC8L, "player_breath_fire_delay 1;player_breath_gasp_lerp 1;player_breath_gasp_scale 0.1;player_breath_gasp_time 1;player_breath_snd_delay 1");
        }
    }

    public static void wawVisionReset(ConsoleService service) {
        service.cbuf(0x822A4AC8L, "r_debugShader 0;r_fullbright 0;r_filmTweakInvert 0;r_filmTweakbrightness 0;r_filmusetweaks 0;r_filmTweakenable 0;r_filmtweakLighttint 1.1 1.05 0.85;r_filmtweakdarktint 0.7 0.85 1");
        service.cbuf(0x822A4AC8L, "scr_art_tweak 0;r_glowUseTweaks 1;r_contrast 1;r_specularmap 0;r_flameFX_enable 0;r_waterSheetingFX_enable 0;r_poisonFX_debug_enable 0");
    }

    public static void wawZombiesCbuf(ActionContext ctx, String command) {
        ctx.service().cbuf(0x82278960L, command);
    }

    public static void wawSendZombiesCommand(ActionContext ctx) {
        wawZombiesCbuf(ctx, ctx.input("zombieCommand"));
    }

    private static void wawApplyClientStats(ConsoleService service, int client, ActionContext ctx, int time) {
        service.callVoid(0x822B6690L, client, 2326, ctx.intInput("prestige"));
        service.callVoid(0x822B6690L, client, 2301, ctx.intInput("xp"));
        service.callVoid(0x822B6690L, client, 2302, ctx.intInput("score"));
        service.callVoid(0x822B6690L, client, 2303, ctx.intInput("kills"));
        service.callVoid(0x822B6690L, client, 2304, ctx.intInput("bestStreak"));
        service.callVoid(0x822B6690L, client, 2305, ctx.intInput("deaths"));
        service.callVoid(0x822B6690L, client, 2308, ctx.intInput("headshots"));
        service.callVoid(0x822B6690L, client, 2311, time);
        service.callVoid(0x822B6690L, client, 2316, ctx.intInput("wins"));
        service.callVoid(0x822B6690L, client, 2317, ctx.intInput("losses"));
        wawServerCommand(service, client, 0, "v loc_warnings 0");
        wawServerCommand(service, client, 0, "v loc_warnings_UI 0");
        wawServerCommand(service, client, 0, "c \"Stats ^2Set Successfully!\"");
    }

    private static void wawStatSet(ConsoleService service, int stat, int value) {
        service.cbuf(0x822A4AC8L, "statset " + stat + " " + value);
    }

    private static long wawPlayerState(ConsoleService service, int clientIndex) {
        return service.readUInt32(wawEntity(clientIndex) + 388L);
    }

    private static long wawEntity(int clientIndex) {
        return 0x82C78770L + (long) clientIndex * 816L;
    }

    private static void wawServerCommand(ConsoleService service, int client, int mode, String command) {
        service.titleCallVoid(0x822B9028L, client, mode, command);
    }

    private static void wawEachClient(ActionContext ctx, WawClientAction action) throws Exception {
        if (ctx.game().clients() == null) {
            action.accept(ctx.clientIndex());
            return;
        }
        if (ctx.allClients()) {
            for (int i = 0; i < ctx.game().clients().maxClients(); i++) action.accept(i);
        } else {
            action.accept(ctx.clientIndex());
        }
    }

    @FunctionalInterface
    private interface WawClientAction {
        void accept(int client) throws Exception;
    }
}
