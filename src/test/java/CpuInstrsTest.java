import helpers.Logger;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintStream;

import static org.junit.Assert.fail;

/**
 * Created by Pablo Canseco on 4/6/2018.
 */
public class CpuInstrsTest extends UnitTest {
    // this class will take all the Blargg cpu_instrs subtests and run them sequentially,
    // comparing the test results from the console. The full test rom will also be ran.

    private Cpu cpuUut;

    private void initRomSubtest(final String romName) {
        Display.reset();
        TimerService.reset();
        InterruptManager.reset();

        Display.getTestInstace();
        Gpu gpu = new Gpu();
        String baseFilePath = "src/test/resources/gb-test-roms/cpu_instrs/individual/";
        MbcManager cartMbc = new MbcManager(new Cartridge(baseFilePath + romName));
        MemoryManager mmu = new MemoryManager(cartMbc, gpu);
        cpuUut = new Cpu(mmu, gpu, Logger.Level.FATAL);
        cpuUut.skipBootrom();
    }
    private void initFullTest() {
        Display.reset();
        TimerService.reset();
        InterruptManager.reset();
        Display.getTestInstace();
        Gpu gpu = new Gpu();
        String path = "src/test/resources/gb-test-roms/cpu_instrs/cpu_instrs.gb";
        MbcManager cartMbc = new MbcManager(new Cartridge(path));
        MemoryManager mmu = new MemoryManager(cartMbc, gpu);
        cpuUut = new Cpu(mmu, gpu, Logger.Level.FATAL);
        // do not skip bootrom, to further exercise bootrom completion.
    }
    private void runTest(boolean fullTest) {
        int i = 0;
        StringBuilder runningLog = new StringBuilder();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream stdout = new PrintStream(new FileOutputStream(FileDescriptor.out));
        while (true) {

            // send stuff to the console so travis doesn't error out.
            if ((i % 500000 == 0) && (i != 0)) { // every 500k cycles print dot.
                System.out.print(".");
                if (i % 20000000 == 0) { // every 20m cycles print new line.
                    System.out.println();
                }
            }

            // capture stdout to get test results
            baos.reset();
            System.setOut(new PrintStream(baos));

            // cycle the cpu
            cpuUut.step();

            // collect output
            String output = baos.toString();

            // revert stdout to default
            System.setOut(stdout);

            // process runningLog
            if (output.length() > 0) {
                runningLog.append(output);
                System.out.print(output);
            }

            // if test is done, break out of loop
            if (!fullTest) {
                if (runningLog.toString().contains("Passed") ||
                    runningLog.toString().contains("Failed")) {
                    System.out.println();
                    System.out.println();
                    break;
                }
            }
            else {
                if (runningLog.toString().contains("Failed") ||
                    runningLog.toString().contains("Passed all tests")) {
                    System.out.println();
                    System.out.println();
                    break;
                }
            }

            // timeout to catch infinite loops
            int timeoutCycles = fullTest ? 60000000 : 30000000;
            if (i >= timeoutCycles) {
                // revert stdout
                System.setOut(stdout);
                fail("test timed out at " + i + " cycles: \n" + runningLog.toString());
                break;
            }

            i++;
        }

        // revert stdout
        System.setOut(stdout);

        if(runningLog.toString().contains("Passed")) {
            log("\n" + runningLog.toString());
            log("Test took " + i + " cycles to complete.");
        }
        else {
            fail(runningLog.toString());
        }
    }

    @Test
    public void special01() {
        initRomSubtest("01-special.gb");

        runTest(false);
    }

    @Test
    public void interrupts02() {
        initRomSubtest("02-interrupts.gb");

        runTest(false);
    }

    @Test
    public void opsphl03() {
        initRomSubtest("03-op sp,hl.gb");

        runTest(false);
    }

    @Test
    public void oprimm04() {
        initRomSubtest("04-op r,imm.gb");

        runTest(false);
    }

    @Test
    public void oprp05() {
        initRomSubtest("05-op rp.gb");

        runTest(false);
    }

    @Test
    public void ldrr06() {
        initRomSubtest("06-ld r,r.gb");

        runTest(false);
    }

    @Test
    public void jrjpcallretrst07() {
        initRomSubtest("07-jr,jp,call,ret,rst.gb");

        runTest(false);
    }

    @Test
    public void miscinstrs08() {
        initRomSubtest("08-misc instrs.gb");

        runTest(false);
    }

    @Test
    public void oprr09() {
        initRomSubtest("09-op r,r.gb");

        runTest(false);
    }

    @Test
    public void bitops10() {
        initRomSubtest("10-bit ops.gb");

        runTest(false);
    }

    @Test
    public void opahl11() {
        initRomSubtest("11-op a,(hl).gb");

        runTest(false);
    }

    @Test
    public void fulltest() {
        initFullTest();

        runTest(true);
    }
}
