package openrtm.console;

import com.jjrpc.JRPC;
import org.junit.jupiter.api.Test;

import java.net.SocketException;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleServiceTest {
    @Test
    void unknownFileEofCommandIsNotMistakenForSocketEof() {
        Exception failure = new JRPC.ComException(0x82DA0007,
                "fileeof failed: 407- unknown command");

        assertFalse(ConsoleService.isRetryableRemoteFailure(failure));
    }

    @Test
    void socketFailureRemainsRetryableWhenWrapped() {
        Exception failure = new RuntimeException("transfer failed",
                new SocketException("Connection reset"));

        assertTrue(ConsoleService.isRetryableRemoteFailure(failure));
    }
}
