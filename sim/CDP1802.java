// Copyright (c) 2021 Douglas Miller <durgadas311@gmail.com>

import java.util.Arrays;

public class CDP1802 {

	public enum State { LOAD, RESET, PAUSE, RUN };

	private final Computer computerImpl;
	private int ticks;
	private int opCode;
	private int regD, regX, regP, regN, regT;
	private boolean DF;
	private boolean Q;
	private int[] regs;
	private boolean[] EF;
	private boolean ffIE = false;
	private boolean pendingEI = false;
	private boolean activeDMAin = false;
	private boolean activeDMAout = false;
	private boolean activeINT = false;
	private boolean idled = false;
	private boolean clear = false;
	private boolean wait = false;
	private String spcl = "";

	public CDP1802(Computer impl) {
		computerImpl = impl;
		regs = new int[16];
		EF = new boolean[4];
		reset();
	}

	public final int getRegD() {
		return regD;
	}

	public final void setRegD(int value) {
		regD = value & 0xff;
	}

	public final int getRegX() {
		return regX;
	}

	public final void setRegX(int value) {
		regX = value & 0x0f;
	}

	public final int getRegP() {
		return regP;
	}

	public final void setRegP(int value) {
		regP = value & 0x0f;
	}

	public final int getReg(int ix) {
		return regs[ix & 0x0f];
	}

	public final int getRegXX() {
		return regs[regX];
	}

	public final int getRegPC() {
		return regs[regP];
	}

	public final void setRegPC(int address) {
		regs[regP] = address & 0xffff;
	}

	public final int getRegSP() {
		return regs[2];
	}

	public final void setRegSP(int word) {
		regs[2] = word & 0xffff;
	}

	public final boolean isCarryFlag() {
		return DF;
	}

	public final void setCarryFlag(boolean state) {
		DF = state;
	}

	public final boolean isZeroFlag() {
		return (regD == 0);
	}

	public boolean isIE() { return ffIE; }

	public final boolean isINTLine() {
		return activeINT;
	}

	public final void setEF(int ix, boolean state) {
		EF[ix & 0x03] = state;
	}

	public final void setDMAin(boolean state) {
		activeDMAin = state;
	}

	public final void setDMAout(boolean state) {
		activeDMAout = state;
	}

	public final void setINTLine(boolean intLine) {
		activeINT = intLine;
	}

	public final void setCLEAR(boolean state) {
		changeState(state, wait);
	}

	public final void setWAIT(boolean state) {
		changeState(clear, state);
	}

	public State getState() {
		if (clear) {
			if (wait) return State.LOAD;
			else return State.RESET;
		} else {
			if (wait) return State.PAUSE;
			else return State.RUN;
		}
	}

	public void changeState(boolean clr, boolean wt) {
		boolean chg = false;
		if (clr != clear) {
			chg = true;
			clear = clr;
		}
		if (wt != wait) {
			chg = true;
			wait = wt;
		}
		if (chg) {
			if (clear && !wait) {
				reset();
			}
		}
	}

	public final boolean isIdled() {
		return idled;
	}

	public void setIdled(boolean state) {
		idled = state;
	}

	public final boolean isPendingEI() {
		return pendingEI;
	}

	public final void setPendingEI(boolean state) {
		pendingEI = state;
	}

	private void incr(int ix) {
		regs[ix] = (regs[ix] + 1) & 0xffff;
	}

	private void incr2(int ix) {
		regs[ix] = (regs[ix] + 2) & 0xffff;
	}

	private void decr(int ix) {
		regs[ix] = (regs[ix] - 1) & 0xffff;
	}

	// Reset
	public final void reset() {
		Arrays.fill(regs, 0);
		Arrays.fill(EF, false);
		regD = 0;
		regX = 0;
		regP = 0;
		regN = 0;
		DF = false;
		changeQ(false);
		ffIE = true;	// TODO: timing issues?
		// technically, these should come from the computer
		activeDMAin = false;
		activeDMAout = false;
		activeINT = false;
		idled = false;
	}

	private void interruption() {
		//System.out.println(String.format("INT at %d T-States", tEstados));
		if (idled) {
			idled = false;
			incr(regP);
		}
		spcl = "INT";

		ffIE = false;
		// TODO: ...
		regT = (regX << 4) | regP;
		regX = 2;
		regP = 1;
		ticks += 8;
	}

	public String specialCycle() { return spcl; }

	private int peek8(int ix) {
		ticks += 8;
		return computerImpl.peek8(regs[ix]);
	}

	private void poke8(int ix, int val) {
		ticks += 8;
		computerImpl.poke8(regs[ix], val);
	}

	private int peek8i(int ix) {
		int v = peek8(ix);
		incr(ix);
		return v;
	}

	private int fetch8() {
		return peek8i(regP);
	}

	private int peek16(int ix) {
		int v;
		ticks += 16;
		v = computerImpl.peek8(regs[ix]) << 8;
		v |= computerImpl.peek8(regs[ix] + 1);
		return v;
	}

	private void changeQ(boolean on) {
		computerImpl.setQ(on);
	}

	private int getR_0(int ix) {
		return (regs[ix] & 0x00ff);
	}

	private void setR_0(int ix, int val) {
		regs[ix] = (regs[ix] & 0xff00) | (val & 0xff);
	}

	private int getR_1(int ix) {
		return (regs[ix] & 0xff00) >> 8;
	}

	private void setR_1(int ix, int val) {
		regs[ix] = (regs[ix] & 0x00ff) | ((val & 0xff) << 8);
	}

	private void add(int v, int ix) {
		v += regD + peek8(ix);
		regD = v & 0xff;
		DF = (v & 0x100) != 0;
	}

	private void sub(int v, int ix) {
		v = peek8(ix) - regD - v;
		regD = v & 0xff;
		DF = (v & 0x100) == 0;
	}

	private void subm(int v, int ix) {
		v = regD - peek8(ix) - v;
		regD = v & 0xff;
		DF = (v & 0x100) == 0;
	}

	private void dmaIn() {
		computerImpl.poke8(regs[0], computerImpl.dmaIn());
		incr(0);
		spcl = "DMA-IN";
		ticks += 8;
		if (idled) {
			idled = false;
			incr(regP);
		}
	}

	private void dmaOut() {
		computerImpl.dmaOut(computerImpl.peek8(regs[0]));
		incr(0);
		spcl = "DMA-OUT";
		ticks += 8;
		if (idled) {
			idled = false;
			incr(regP);
		}
	}

	public final int execute() {
		ticks = 0;

		if (activeDMAin) {
			dmaIn();
			return -ticks;
		} else if (activeDMAout) {
			dmaOut();
			return -ticks;
		} else if (ffIE && activeINT) {
			interruption();
			return -ticks;
		}

		if (clear) {	// just like IDLE?
			if (wait) {
				spcl = "LOAD";
			} else {
				spcl = "RESET";
			}
			ticks += 8;
			return -ticks;
		}

		opCode = fetch8();
		decodeOpcode(opCode);

		return ticks;
	}

	private void decodeOpcode(int opCode) {
		int T = (opCode & 0x0f);
		int v;

		switch (opCode) {
		case 0x00:	// IDLE
			idled = true;
			decr(regP);
			// ticks += ?;
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
			regD = peek8(regs[T]);
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
			incr(T);
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
			decr(T);
			break;
		case 0x30:	// BR (short)
			setR_0(regP, fetch8());
			break;
		case 0x31:	// BQ (short)
			if (Q) {
				setR_0(regP, fetch8());
			} else {
				incr(regP);
			}
			break;
		case 0x32:	// BZ (short)
			if (regD == 0) {
				setR_0(regP, fetch8());
			} else {
				incr(regP);
			}
			break;
		case 0x33:	// BDF (short)
			if (DF) {
				setR_0(regP, fetch8());
			} else {
				incr(regP);
			}
			break;
		case 0x34:	// B1 (short)
			if (EF[0]) {
				setR_0(regP, fetch8());
			} else {
				incr(regP);
			}
			break;
		case 0x35:	// B2 (short)
			if (EF[1]) {
				setR_0(regP, fetch8());
			} else {
				incr(regP);
			}
			break;
		case 0x36:	// B3 (short)
			if (EF[2]) {
				setR_0(regP, fetch8());
			} else {
				incr(regP);
			}
			break;
		case 0x37:	// B4 (short)
			if (EF[3]) {
				setR_0(regP, fetch8());
			} else {
				incr(regP);
			}
			break;
		case 0x38:	// NBR (short)
			incr(regP);
			break;
		case 0x39:	// BNQ (short)
			if (!Q) {
				setR_0(regP, fetch8());
			} else {
				incr(regP);
			}
			break;
		case 0x3a:	// BNZ (short)
			if (regD != 0) {
				setR_0(regP, fetch8());
			} else {
				incr(regP);
			}
			break;
		case 0x3b:	// BNF (short)
			if (!DF) {
				setR_0(regP, fetch8());
			} else {
				incr(regP);
			}
			break;
		case 0x3c:	// BN1 (short)
			if (!EF[0]) {
				setR_0(regP, fetch8());
			} else {
				incr(regP);
			}
			break;
		case 0x3d:	// BN2 (short)
			if (!EF[1]) {
				setR_0(regP, fetch8());
			} else {
				incr(regP);
			}
			break;
		case 0x3e:	// BN3 (short)
			if (!EF[2]) {
				setR_0(regP, fetch8());
			} else {
				incr(regP);
			}
			break;
		case 0x3f:	// BN4 (short)
			if (!EF[3]) {
				setR_0(regP, fetch8());
			} else {
				incr(regP);
			}
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
			regD = peek8(T);
			incr(T);
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
			poke8(T, regD);
			break;
		case 0x60:	// IRX
			incr(regX);
			break;
		case 0x61:	// OUT 1...
		case 0x62:
		case 0x63:
		case 0x64:
		case 0x65:
		case 0x66:
		case 0x67:
			// *does* increment R(X)
			regN = T & 0x07;
			computerImpl.outPort(regN, peek8i(regX));
			break;
		case 0x68:
			// TODO: anything?
			break;
		case 0x69:	// INP 1...
		case 0x6a:
		case 0x6b:
		case 0x6c:
		case 0x6d:
		case 0x6e:
		case 0x6f:
			// *does not* increment R(X)
			regN = T & 0x07;
			regD = computerImpl.inPort(regN);
			poke8(regX, regD);
			break;
		case 0x70:	// RET
			ffIE = true;
			v = peek8(regX);
			incr(regX);
			regX = (v >> 4) & 0x0f;
			regP = v & 0x0f;
			break;
		case 0x71:	// DIS
			ffIE = false;
			v = peek8(regX);
			regX = (v >> 4) & 0x0f;
			regP = v & 0x0f;
			incr(regX);	// TODO: before or after set?
			break;
		case 0x72:	// LDXA
			regD = peek8(regX);
			incr(regX);
			break;
		case 0x73:	// STXD
			poke8(regX, regD);
			decr(regX);
			break;
		case 0x74:	// ADC
			add(DF ? 1 : 0, regX);
			break;
		case 0x75:	// SDB
			sub(DF ? 0 : 1, regX);
			break;
		case 0x76:	// SHRC, RSHR
			v = regD;
			regD = (regD >> 1) & 0x7f;
			if (DF) regD |= 0x80;
			DF = (v & 0x01) != 0;
			break;
		case 0x77:	// SMB
			subm(DF ? 0 : 1, regX);
			break;
		case 0x78:	// SAV
			poke8(regX, regT);
			break;
		case 0x79:	// MARK
			regT = (regX << 4) | regP;
			poke8(2, regT);
			decr(2);
			regX = regP;
			break;
		case 0x7A:	// REQ
			changeQ(false);
			break;
		case 0x7B:	// SEQ
			changeQ(true);
			break;
		case 0x7C:	// ADCI
			add(DF ? 1 : 0, regP);
			incr(regP);
			break;
		case 0x7D:	// SDBI
			sub(DF ? 0 : 1, regP);
			incr(regP);
			break;
		case 0x7E:	// SHLC, RSHL
			v = regD;
			regD = (regD << 1) & 0xff;
			if (DF) regD |= 0x01;
			DF = (v & 0x80) != 0;
			break;
		case 0x7F:	// SMBI
			subm(DF ? 0 : 1, regP);
			incr(regP);
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
			regD = getR_0(T);
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
			regD = getR_1(T);
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
			setR_0(T, regD);
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
			setR_1(T, regD);
			break;
		case 0xc0:	// LBR
			regs[regP] = peek16(regP);
			break;
		case 0xc1:	// LBQ
			if (Q) {
				regs[regP] = peek16(regP);
			} else {
				incr2(regP);
			}
			break;
		case 0xc2:	// LBZ
			if (regD == 0) {
				regs[regP] = peek16(regP);
			} else {
				incr2(regP);
			}
			break;
		case 0xc3:	// LBDF
			if (DF) {
				regs[regP] = peek16(regP);
			} else {
				incr2(regP);
			}
			break;
		case 0xc4:	// NOP
			break;
		case 0xc5:	// LSNQ
			if (!Q) {
				incr2(regP);
			}
			break;
		case 0xc6:	// LSNZ
			if (regD != 0) {
				incr2(regP);
			}
			break;
		case 0xc7:	// LSNF
			if (!DF) {
				incr2(regP);
			}
			break;
		case 0xc8:	// LNBR
			incr2(regP);
			break;
		case 0xc9:	// LBNQ
			if (!Q) {
				regs[regP] = peek16(regP);
			} else {
				incr2(regP);
			}
			break;
		case 0xca:	// LBNZ
			if (regD != 0) {
				regs[regP] = peek16(regP);
			} else {
				incr2(regP);
			}
			break;
		case 0xcb:	// LBNF
			if (!DF) {
				regs[regP] = peek16(regP);
			} else {
				incr2(regP);
			}
			break;
		case 0xcc:	// LSIE
			if (ffIE) {
				incr2(regP);
			}
			break;
		case 0xcd:	// LSQ
			if (Q) {
				incr2(regP);
			}
			break;
		case 0xce:	// LSZ
			if (regD == 0) {
				incr2(regP);
			}
			break;
		case 0xcf:	// LSDF
			if (DF) {
				incr2(regP);
			}
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
			regP = T;
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
			regX = T;
			break;
		case 0xf0:	// LDX
			regD = peek8(regX);
			break;
		case 0xf1:	// OR
			regD |= peek8(regX);
			break;
		case 0xf2:	// AND
			regD &= peek8(regX);
			break;
		case 0xf3:	// XOR
			regD ^= peek8(regX);
			break;
		case 0xf4:	// ADD
			add(0, regX);
			break;
		case 0xf5:	// SD
			sub(0, regX);
			break;
		case 0xf6:	// SHR
			v = regD;
			regD = (regD >> 1) ^ 0x7f;
			DF = (v & 0x01) != 0;
			break;
		case 0xf7:	// SM
			subm(0, regX);
			break;
		case 0xf8:	// LDI
			regD = fetch8();
			break;
		case 0xf9:	// ORI
			regD |= fetch8();
			break;
		case 0xfa:	// ANI
			regD &= fetch8();
			break;
		case 0xfb:	// XRI
			regD ^= fetch8();
			break;
		case 0xfc:	// ADI
			add(0, regP);
			incr(regP);
			break;
		case 0xfd:	// SDI
			sub(0, regP);
			incr(regP);
			break;
		case 0xfe:	// SHL
			v = regD;
			regD = (regD << 1) & 0xff;
			DF = (v & 0x80) != 0;
			break;
		case 0xff:	// SMI
			subm(0, regP);
			incr(regP);
			break;
		// default: treat like NOP...
		}
	}

	public String dumpDebug() {
		String s = new String();
		s += String.format("INT=%s IE=%s CLEAR=%s WAIT=%s\n",
				isINTLine(), isIE(), clear, wait);
		s += String.format("P=%d X=%d\n", regP, regX);
		s += String.format("%04x %04x %04x %04x\n",
			regs[0], regs[1], regs[2], regs[3]);
		s += String.format("%04x %04x %04x %04x\n",
			regs[4], regs[5], regs[6], regs[7]);
		s += String.format("%04x %04x %04x %04x\n",
			regs[8], regs[9], regs[10], regs[11]);
		s += String.format("%04x %04x %04x %04x\n",
			regs[12], regs[13], regs[14], regs[15]);
		s += String.format("D=%02x DF=%s Q=%s\n", regD, DF, Q);
		s += String.format("EF1=%s EF2=%s EF3=%s EF4=%s\n",
			EF[0], EF[1], EF[2], EF[3]);
		return s;
	}
}
