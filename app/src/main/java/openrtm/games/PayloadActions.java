package openrtm.games;

import openrtm.console.ConsoleService;
import openrtm.util.HexUtils;

public final class PayloadActions {
    private static final PayloadStore PAYLOADS = new PayloadStore();

    private PayloadActions() {
    }

    public static int applyOperations(ConsoleService service, String prefix) {
        int writes = 0;
        for (PayloadStore.MemoryPayload payload : PAYLOADS.operations(prefix)) {
            service.writeMemory(payload.address(), payload.data());
            writes++;
        }
        return writes;
    }

    public static int bo1LegacyClassUnlock(ConsoleService service) {
        return applyOperations(service, "bo1.legacyClassUnlock");
    }

    public static int bo2GunsAndCamos(ConsoleService service) {
        service.writeMemory(0x8435429FL, PAYLOADS.blob("bo2.unlock"));
        service.writeMemory(0x843543ACL, HexUtils.bytes(0xFF));
        service.writeMemory(0x843543A9L, HexUtils.bytes(0xFF));
        service.writeMemory(0x8434AF80L, PAYLOADS.blob("bo2.unlock2"));
        return 4;
    }

    public static int bo2UnlockAll(ConsoleService service) {
        service.writeMemory(0x8434AE9EL, PAYLOADS.blob("bo2.unlockAll.data"));
        service.cbuf(0x824015E0L, "setStatFromLocString itemstats 42 stats kills statvalue ");
        service.cbuf(0x824015E0L, "setStatFromLocString itemstats 44 stats kills statvalue ");
        service.cbuf(0x824015E0L, "setStatFromLocString itemstats 42 stats deathsduringuse statvalue 4231");
        service.cbuf(0x824015E0L, "setStatFromLocString itemstats 44 stats deathsduringuse statvalue 3252");
        return 5;
    }

    public static int bo2UnlockAllAndStickStats(ConsoleService service) {
        int writes = bo2UnlockAll(service);
        GameActions.bo2StickStats(service);
        return writes;
    }

    public static int bo2TrueUnlockAll(ConsoleService service) {
        bo2UnlockAll(service);
        byte[] ff196 = new byte[196];
        for (int i = 0; i < ff196.length; i++) ff196[i] = (byte) 0xFF;
        service.writeMemory(0x8435429FL, ff196);
        service.writeMemory(0x843543ACL, HexUtils.bytes(0xFF));
        service.writeMemory(0x843543A9L, HexUtils.bytes(0xFF));
        GameActions.bo2CalzoneClasses(service);
        service.writeMemory(0x84353AB2L, new byte[4]);
        service.writeMemory(0x84353ACFL, new byte[10]);
        service.writeMemory(0x84353AC1L, HexUtils.bytes(0x16, 0x00, 0x00));
        GameActions.bo2StickStats(service);
        return 8;
    }

    public static int mw3UnlockAll(ConsoleService service) {
        return applyOperations(service, "mw3.unlockAll");
    }

    public static int mw3GodmodeClasses(ConsoleService service) {
        return applyOperations(service, "mw3.godmodeClasses");
    }
}
