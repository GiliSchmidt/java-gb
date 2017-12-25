public class MMU {

    private static final int CONST_WRAM_SIZE = 8192;
    private static final int CONST_ERAM_SIZE = 32768;
    private static final int CONST_ZRAM_SIZE = 128;

    private static Cartridge cart;

    // Flag indicating the BIOS is still mapped
    // BIOS is unmapped with first instruction above 0x00FF
    static boolean inBios = true;

    // Memory Regions
    static int[]
            wram = new int[CONST_WRAM_SIZE], // Working RAM
            eram = new int[CONST_ERAM_SIZE], // External RAM
            zram = new int[CONST_ZRAM_SIZE]; // Zero-page RAM

    static int[] bios = hexStringToByteArray(
            "31FEFFAF21FF9F32CB7C20FB2126FF0E" +
            "113E8032E20C3EF3E2323E77773EFCE0" +
            "471104012110801ACD9500CD9600137B" +
            "FE3420F311D80006081A1322230520F9" +
            "3E19EA1099212F990E0C3D2808320D20" +
            "F92E0F18F3673E6457E0423E91E04004" +
            "1E020E0CF044FE9020FA0D20F71D20F2" +
            "0E13247C1E83FE6228061EC1FE642006" +
            "7BE20C3E87F2F04290E0421520D20520" +
            "4F162018CB4F0604C5CB1117C1CB1117" +
            "0520F522232223C9CEED6666CC0D000B" +
            "03730083000C000D0008111F8889000E" +
            "DCCC6EE6DDDDD999BBBB67636E0EECCC" +
            "DDDC999FBBB9333E3c42B9A5B9A5424C" +
            "21040111A8001A13BE20FE237DFE3420" +
            "F506197886230520FB8620FE3E01E050".toLowerCase());

    static int[] logo = MMU.hexStringToByteArray(
            "CEED6666CC0D000B03730083000C000D" +
                    "0008111F8889000EDCCC6EE6DDDDD999" +
                    "BBBB67636E0EECCCDDDC999FBBB9333E");

    public static void reset(Cartridge cartRef) {
        for (int i = 0; i < CONST_WRAM_SIZE; i++) wram[i] = 0;
        for (int i = 0; i < CONST_ERAM_SIZE; i++) eram[i] = 0;
        for (int i = 0; i < CONST_ZRAM_SIZE; i++) zram[i] = 0;

        MMU.cart = cartRef;

        inBios = true;
        System.out.println("MMU Reset. BIOS (Expected 49, 80): " + bios[0] + " " + bios[255]);
    }

    public static int rb(int addr) {
        /* Read 8-bit byte from a given address */

        switch (addr & 0xF000) {

            // BIOS (256b) ROM 0
            case 0x0000:
                if (inBios) {
                    if (addr < 0x0100) return bios[addr];
                    else if (LegacyZ80.Reg.pc == 0x0100) {
                        inBios = false;
                        System.out.println("We've departed the BIOS!");
                    }
                } else {
                    return cart.readFromAddress(addr);
                }

                // ROM 0
            case 0x1000:
            case 0x2000:
            case 0x3000:
                return cart.readFromAddress(addr);

            // ROM 1 (unbanked) (16K)
            case 0x4000:
            case 0x5000:
            case 0x6000:
            case 0x7000:
                return cart.readFromAddress(addr);

            // Graphics VRAM (8k)
            case 0x8000:
            case 0x9000:
                return GPU.vram[addr & 0x1FFF];

            // External RAM (8k)
            case 0xA000:
            case 0xB000:
                return eram[addr & 0x1FFF];

            // Working RAM (8k)
            case 0xC000:
            case 0xD000:
                return wram[addr & 0x1FFF];

            // Working RAM shadow copy (8k)
            case 0xE000:
                return wram[addr & 0x1FFF];

            // Working RAM shadow, I/O, Zero-page RAM
            case 0xF000:
                switch (addr & 0x0F00) {
                    // Working RAM shadow
                    case 0x000:
                    case 0x100:
                    case 0x200:
                    case 0x300:
                    case 0x400:
                    case 0x500:
                    case 0x600:
                    case 0x700:
                    case 0x800:
                    case 0x900:
                    case 0xA00:
                    case 0xB00:
                    case 0xC00:
                    case 0xD00:
                        return wram[addr & 0x1FFF];

                    // Graphics object attribute memory
                    // OAM is 160 bytes, remaining bytes read as 0
                    case 0xE00:
                        if (addr >= 0xFF80)
                            return zram[addr & 0x7F];
                        else {
                            // I/O control handling
                            // currently unhandled TODO
                            return 0;
                        }

                        // Zeropage RAM, I/O, Interrupts
                    case 0xF00:
                        if (addr >= 0xFF80) return zram[addr & 0x7F];
                        else {
                            // I/O control handling
                            switch (addr & 0x00F0) {
                                case 0x10:
                                case 0x20:
                                case 0x30:
                                    return 0;


                                //GPU's 64 registers
                                case 0x40:
                                case 0x50:
                                case 0x60:
                                case 0x70:
                                    return GPU.rb(addr);
                            }
                        }
                }
        }
        System.out.println("Reached end of MMU.rb function. This is a problem.");
        return 0;
    }

    public static int rw(int addr) {
		/* Read 16-bit word from a given address */
        // this just reads the requested address and
        // adds the next address to the 8 MSB's to create 16 bits
        int res = 0;
        int upper = rb(addr + 1);
        res += upper;
        res = res << 8;
        int lower = rb(addr);
        res += lower;
        return res;
    }

    public static void wb(int addr, int val) {
		/* Write 8-bit byte to a given address */
        val &= 0xFF;
        switch (addr & 0xF000) {
            // ROM banks are read only so you can't really write to them.
            // ROM bank 0
            case 0x0000:
                if (inBios && (addr < 0x0100)) return;
                // fall through
            case 0x1000:
            case 0x2000:
            case 0x3000:
                break;

            // ROM bank 1
            case 0x4000:
            case 0x5000:
            case 0x6000:
            case 0x7000:
                break;

            // VRAM
            case 0x8000:
            case 0x9000:
                GPU.vram[addr & 0x1FFF] = val; //TODO uncomment these
                GPU.updatetile((addr & 0x1FFF), val);
                break;

            // External RAM
            case 0xA000:
            case 0xB000:
                eram[addr & 0x1FFF] = val;
                break;

            // Working RAM and Shadow copy
            case 0xC000:
            case 0xD000:
            case 0xE000:
                wram[addr & 0x1FFF] = val;
                break;

            // Everything else
            case 0xF000:
                switch (addr & 0x0F00) {
                    // Shadow WRAM
                    case 0x000:
                    case 0x100:
                    case 0x200:
                    case 0x300:
                    case 0x400:
                    case 0x500:
                    case 0x600:
                    case 0x700:
                    case 0x800:
                    case 0x900:
                    case 0xA00:
                    case 0xB00:
                    case 0xC00:
                    case 0xD00:
                        wram[addr & 0x1FFF] = val;
                        break;

                    // Object Attribute Memory
                    case 0xE00:
                        //if((addr & 0xFF) < 0xA0) GPU.oam[addr & 0xFF] = val; // TODO uncomment these
                        //GPU.updateOAM(addr, val);
                        break;

                    // Zero-page RAM, I/O
                    case 0xF00:
                        if (addr >= 0xFF80)
                            zram[addr & 0x7F] = val;
                        else {
                            // I/O
                            switch (addr & 0x00F0) {
                                // GPU
                                case 0x40:
                                case 0x50:
                                case 0x60:
                                case 0x70:
                                    GPU.wb(addr, val);
                                    break;
                            }
                        }
                }
                break;
        }
    }

    public static void ww(int addr, int val) {
		/* Write 16-bit word to a given address */
        wb(addr, (val & 255)); // mask to 8 bits.
        wb(addr + 1, (val >> 8)); // write remaining 8 bits.
    }


    // Helper Functions
    public static int[] hexStringToByteArray(String s) {
        int len = s.length();
        int[] data = new int[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i + 1), 16);
        }
        return data;
    }
}