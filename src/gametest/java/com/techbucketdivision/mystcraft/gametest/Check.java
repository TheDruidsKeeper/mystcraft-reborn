package com.tbd.mystcraft.gametest;

import com.tbd.mystcraft.Mystcraft;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.neoforged.testframework.gametest.ExtendedGameTestHelper;

/** Runs a test body so that unexpected exceptions reach the log with a stack trace and a readable failure message. */
final class Check {
    private Check() {}

    interface Body {
        void run() throws Exception;
    }

    static void run(ExtendedGameTestHelper helper, Body body) {
        try {
            body.run();
        } catch (GameTestAssertException e) {
            throw e;
        } catch (Exception | AssertionError e) {
            Mystcraft.LOGGER.error("[gametest] unexpected exception", e);
            StackTraceElement at = e.getStackTrace().length > 0 ? e.getStackTrace()[0] : null;
            helper.fail(e.getClass().getSimpleName() + ": " + e.getMessage() + (at == null ? "" : " @ " + at));
        }
    }
}
