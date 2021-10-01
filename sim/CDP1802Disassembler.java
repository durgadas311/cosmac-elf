// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>

public class CDP1802Disassembler {
	Memory mem;
	boolean rom;	// TODO: support ROM
	int lastLen;

	public CDP1802Disassembler(Memory mem) {
		this.mem = mem;
	}

	private int read8(int adr) {
		++lastLen;
		return mem.read(adr & 0xffff);
	}

	private int read16(int adr) {
		int w;
		// big endian...
		w = mem.read(adr & 0xffff) << 8;
		++adr;
		w |= mem.read(adr & 0xffff);
		lastLen += 2;
		return w;
	}

	private int shortAdr(int adr) {
		int imm = read8(adr++);
		imm |= (adr & 0xff00);
		return imm;
	}

	public int instrLen() { return lastLen; }

	public String disas(int pc) {
		return disas(false, pc);
	}

	public String disas(boolean rom, int pc) {
		String instr = "";
		lastLen = 0;
		this.rom = rom;
		int opCode = read8(pc++);
		int T = opCode & 0x0f;
		switch (opCode) {
		case 0x00:	// IDLE
			instr = "idle";
			break;
		case 0x01:	// LDN
		case 0x02:
		case 0x03:
		case 0x04:
		case 0x05:
		case 0x06:
		case 0x07:
		case 0x08:
		case 0x09:
		case 0x0a:
		case 0x0b:
		case 0x0c:
		case 0x0d:
		case 0x0e:
		case 0x0f:
			instr = String.format("ldn r%x", T);
			break;
		case 0x10:	// INC
		case 0x11:
		case 0x12:
		case 0x13:
		case 0x14:
		case 0x15:
		case 0x16:
		case 0x17:
		case 0x18:
		case 0x19:
		case 0x1a:
		case 0x1b:
		case 0x1c:
		case 0x1d:
		case 0x1e:
		case 0x1f:
			instr = String.format("inc r%x", T);
			break;
		case 0x20:	// DEC
		case 0x21:
		case 0x22:
		case 0x23:
		case 0x24:
		case 0x25:
		case 0x26:
		case 0x27:
		case 0x28:
		case 0x29:
		case 0x2a:
		case 0x2b:
		case 0x2c:
		case 0x2d:
		case 0x2e:
		case 0x2f:
			instr = String.format("dec r%x", T);
			break;
		case 0x30:	// BR (short)
			instr = String.format("br %04x", shortAdr(pc));
			break;
		case 0x31:	// BQ (short)
			instr = String.format("bq %04x", shortAdr(pc));
			break;
		case 0x32:	// BZ (short)
			instr = String.format("bz %04x", shortAdr(pc));
			break;
		case 0x33:	// BDF (short)
			instr = String.format("bdf %04x", shortAdr(pc));
			break;
		case 0x34:	// B1 (short)
			instr = String.format("b1 %04x", shortAdr(pc));
			break;
		case 0x35:	// B2 (short)
			instr = String.format("b2 %04x", shortAdr(pc));
			break;
		case 0x36:	// B3 (short)
			instr = String.format("b3 %04x", shortAdr(pc));
			break;
		case 0x37:	// B4 (short)
			instr = String.format("b4 %04x", shortAdr(pc));
			break;
		case 0x38:	// NBR (short)
			instr = String.format("nbr %04x", shortAdr(pc));
			break;
		case 0x39:	// BNQ (short)
			instr = String.format("bnq %04x", shortAdr(pc));
			break;
		case 0x3a:	// BNZ (short)
			instr = String.format("bnz %04x", shortAdr(pc));
			break;
		case 0x3b:	// BNF (short)
			instr = String.format("bnf %04x", shortAdr(pc));
			break;
		case 0x3c:	// BN1 (short)
			instr = String.format("bn1 %04x", shortAdr(pc));
			break;
		case 0x3d:	// BN2 (short)
			instr = String.format("bn2 %04x", shortAdr(pc));
			break;
		case 0x3e:	// BN3 (short)
			instr = String.format("bn3 %04x", shortAdr(pc));
			break;
		case 0x3f:	// BN4 (short)
			instr = String.format("bn4 %04x", shortAdr(pc));
			break;
		case 0x40:	// LDA
		case 0x41:
		case 0x42:
		case 0x43:
		case 0x44:
		case 0x45:
		case 0x46:
		case 0x47:
		case 0x48:
		case 0x49:
		case 0x4a:
		case 0x4b:
		case 0x4c:
		case 0x4d:
		case 0x4e:
		case 0x4f:
			instr = String.format("lda r%x", T);
			break;
		case 0x50:	// STR
		case 0x51:
		case 0x52:
		case 0x53:
		case 0x54:
		case 0x55:
		case 0x56:
		case 0x57:
		case 0x58:
		case 0x59:
		case 0x5a:
		case 0x5b:
		case 0x5c:
		case 0x5d:
		case 0x5e:
		case 0x5f:
			instr = String.format("str r%x", T);
			break;
		case 0x60:	// IRX
			instr = "irx";
			break;
		case 0x61:	// OUT1...
		case 0x62:
		case 0x63:
		case 0x64:
		case 0x65:
		case 0x66:
		case 0x67:
			instr = String.format("out %x", T & 0x07);
			break;
		case 0x68:
			// TODO: anything?
			instr = "?68";
			break;
		case 0x69:	// OUT1...
		case 0x6a:
		case 0x6b:
		case 0x6c:
		case 0x6d:
		case 0x6e:
		case 0x6f:
			instr = String.format("inp %x", T & 0x07);
			break;
		case 0x70:	// RET
			instr = "ret";
			break;
		case 0x71:	// DIS
			instr = "dis";
			break;
		case 0x72:	// LDXA
			instr = "ldxa";
			break;
		case 0x73:	// STXD
			instr = "stxd";
			break;
		case 0x74:	// ADC
			instr = "adc";
			break;
		case 0x75:	// SDB
			instr = "sdb";
			break;
		case 0x76:	// SHRC, RSHR
			instr = "shrc";
			break;
		case 0x77:	// SMB
			instr = "smb";
			break;
		case 0x78:	// SAV
			instr = "sav";
			break;
		case 0x79:	// MARK
			instr = "mark";
			break;
		case 0x7A:	// REQ
			instr = "req";
			break;
		case 0x7B:	// SEQ
			instr = "seq";
			break;
		case 0x7C:	// ADCI
			instr = String.format("adci %02x", read8(pc++));
			break;
		case 0x7D:	// SDBI
			instr = String.format("sdbi %02x", read8(pc++));
			break;
		case 0x7E:	// SHLC, RSHL
			instr = "shlc";
			break;
		case 0x7F:	// SMBI
			instr = String.format("smbi %02x", read8(pc++));
			break;
		case 0x80:	// GLO
		case 0x81:
		case 0x82:
		case 0x83:
		case 0x84:
		case 0x85:
		case 0x86:
		case 0x87:
		case 0x88:
		case 0x89:
		case 0x8a:
		case 0x8b:
		case 0x8c:
		case 0x8d:
		case 0x8e:
		case 0x8f:
			instr = String.format("glo r%x", T);
			break;
		case 0x90:	// GHI
		case 0x91:
		case 0x92:
		case 0x93:
		case 0x94:
		case 0x95:
		case 0x96:
		case 0x97:
		case 0x98:
		case 0x99:
		case 0x9a:
		case 0x9b:
		case 0x9c:
		case 0x9d:
		case 0x9e:
		case 0x9f:
			instr = String.format("ghi r%x", T);
			break;
		case 0xa0:	// PLO
		case 0xa1:
		case 0xa2:
		case 0xa3:
		case 0xa4:
		case 0xa5:
		case 0xa6:
		case 0xa7:
		case 0xa8:
		case 0xa9:
		case 0xaa:
		case 0xab:
		case 0xac:
		case 0xad:
		case 0xae:
		case 0xaf:
			instr = String.format("plo r%x", T);
			break;
		case 0xb0:	// PHI
		case 0xb1:
		case 0xb2:
		case 0xb3:
		case 0xb4:
		case 0xb5:
		case 0xb6:
		case 0xb7:
		case 0xb8:
		case 0xb9:
		case 0xba:
		case 0xbb:
		case 0xbc:
		case 0xbd:
		case 0xbe:
		case 0xbf:
			instr = String.format("phi r%x", T);
			break;
		case 0xc0:	// LBR
			instr = String.format("lbr %04x", read16(pc));
			break;
		case 0xc1:	// LBQ
			instr = String.format("lbq %04x", read16(pc));
			break;
		case 0xc2:	// LBZ
			instr = String.format("lbz %04x", read16(pc));
			break;
		case 0xc3:	// LBDF
			instr = String.format("lbdf %04x", read16(pc));
			break;
		case 0xc4:	// NOP
			instr = "nop";
			break;
		case 0xc5:	// LSNQ
			instr = "lsnq";
			break;
		case 0xc6:	// LSNZ
			instr = "lsnz";
			break;
		case 0xc7:	// LSNF
			instr = "lsnf";
			break;
		case 0xc8:	// LNBR
			instr = String.format("lnbr %04x", read16(pc));
			break;
		case 0xc9:	// LBNQ
			instr = String.format("lbnq %04x", read16(pc));
			break;
		case 0xca:	// LBNZ
			instr = String.format("lbnz %04x", read16(pc));
			break;
		case 0xcb:	// LBNF
			instr = String.format("lbnf %04x", read16(pc));
			break;
		case 0xcc:	// LSIE
			instr = "lsie";
			break;
		case 0xcd:	// LSQ
			instr = "lsq";
			break;
		case 0xce:	// LSZ
			instr = "lsz";
			break;
		case 0xcf:	// LSDF
			instr = "lsdf";
			break;
		case 0xd0:	// SEP
		case 0xd1:
		case 0xd2:
		case 0xd3:
		case 0xd4:
		case 0xd5:
		case 0xd6:
		case 0xd7:
		case 0xd8:
		case 0xd9:
		case 0xda:
		case 0xdb:
		case 0xdc:
		case 0xdd:
		case 0xde:
		case 0xdf:
			instr = String.format("sep r%x", T);
			break;
		case 0xe0:	// SEX
		case 0xe1:
		case 0xe2:
		case 0xe3:
		case 0xe4:
		case 0xe5:
		case 0xe6:
		case 0xe7:
		case 0xe8:
		case 0xe9:
		case 0xea:
		case 0xeb:
		case 0xec:
		case 0xed:
		case 0xee:
		case 0xef:
			instr = String.format("sex r%x", T);
			break;
		case 0xf0:	// LDX
			instr = "ldx";
			break;
		case 0xf1:	// OR
			instr = "or";
			break;
		case 0xf2:	// AND
			instr = "and";
			break;
		case 0xf3:	// XOR
			instr = "xor";
			break;
		case 0xf4:	// ADD
			instr = "add";
			break;
		case 0xf5:	// SD
			instr = "sd";
			break;
		case 0xf6:	// SHR
			instr = "shr";
			break;
		case 0xf7:	// SM
			instr = "sm";
			break;
		case 0xf8:	// LDI
			instr = String.format("ldi %02x", read8(pc++));
			break;
		case 0xf9:	// ORI
			instr = String.format("ori %02x", read8(pc++));
			break;
		case 0xfa:	// ANI
			instr = String.format("ani %02x", read8(pc++));
			break;
		case 0xfb:	// XRI
			instr = String.format("xri %02x", read8(pc++));
			break;
		case 0xfc:	// ADI
			instr = String.format("adi %02x", read8(pc++));
			break;
		case 0xfd:	// SDI
			instr = String.format("sdi %02x", read8(pc++));
			break;
		case 0xfe:	// SHL
			instr = "shl";
			break;
		case 0xff:	// SMI
			instr = String.format("smi %02x", read8(pc++));
			break;
		default:
			instr = String.format("?%02x", opCode);
			break;
		}
		return instr;
	}
}
