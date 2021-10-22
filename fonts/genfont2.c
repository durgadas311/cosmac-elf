#include <stdio.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdlib.h>
#include <pwd.h>
#include <time.h>
#include <string.h>
#include <unistd.h>

double seg_t = 126.4;
double seg_h = 764.72;
double seg_w = 673.08;
double skew = 2.0 / 23.0;
double gap = 56.88;
double org_x = 337.2;
double org_y = 167.48;
double wid = 2040;
double hei = 2048.0;

// Segment origins (lower left), based on 0 = g, ... 7 = dp,
// 0,0 is lower left of character, not cell.
double seg_org[8][4] = {
[0] = { 183.28, 786.84, 673.08, 126.4 },	// g
[1] = { 183.28, 1580.0, 673.08, 126.4 },	// a
[2] = { 913.24, 878.48, 126.4,  764.72 },	// b
[3] = { 913.24,  56.88, 126.4,  764.72 },	// c
[4] = { 183.28,   0.0,  673.08, 126.4 },	// d
[5] = {   0.0,   56.88, 126.4,  764.72 },	// e
[6] = {   0.0,  878.48, 126.4,  764.72 },	// f
[7] = { 1197.64,  0.0,  167.48,   0.0 },	// dp
};
/*
0 90 m 0
 0 140 40 180 90 180 c 0
 140 180 180 140 180 90 c 0
 180 40 140 0 90 0 c 0
 40 0 0 40 0 90 c 0

*/

void do_seg(double *coords) {
	double x, y;
	if (coords[3] == 0.0) {
		printf("1508 253 m 4\n"
			" 1508 303 1548 343 1598 343 c 4\n"
			" 1648 343 1688 303 1688 253 c 4\n"
			" 1688 203 1648 163 1598 163 c 4\n"
			" 1548 163 1508 203 1508 253 c 4\n");
		return;
	}
	x = coords[0] + org_x;
	y = coords[1] + org_y;
	printf("%f %f m 1\n", x + (y * skew), y);
	y += coords[3];
	printf(" %f %f l 1\n", x + (y * skew), y);
	x += coords[2];
	printf(" %f %f l 1\n", x + (y * skew), y);
	y -= coords[3];
	printf(" %f %f l 1\n", x + (y * skew), y);
	x -= coords[2];
	printf(" %f %f l 1\n", x + (y * skew), y);
}

void do_char(int c, int fc) {
	int r;
	static int cn = 0;

	printf("StartChar: uni%04X\n", fc);
	printf("Encoding: %d %d %d\n", fc, fc, cn++); // what is 3rd number?
	if (c < 0) {
		printf("Width: %d\n", -c);
	} else {
		printf("Width: %f\n", wid);
	}
	printf("VWidth: 0\n");
	printf("Flags: HW\n");
	printf("LayerCount: 2\n");
	printf("Fore\n");
	if (c >= 0) {
		printf("SplineSet\n");
		r = 0;
		while (c != 0) {
			if ((c & 1) != 0) {
				do_seg(seg_org[r]);
			}
			c >>= 1;
			++r;
		}
		printf("EndSplineSet\n");
	}
	printf("EndChar\n\n");
}

static void preamble(int ascent, int descent, char *name, char *arg) {
	struct passwd *pw = getpwuid(getuid());
	time_t t = time(NULL);
	struct tm *tm = localtime(&t);
	char *user = pw->pw_gecos;
	if (*user == 0 || *user == ' ') {
		user = pw->pw_name;
	} else {
		int n = strlen(user);
		while (n > 0 && user[n - 1] == ',') {
			user[--n] = 0;
		}
	}
	printf(	"SplineFontDB: 3.0\n"
		"FontName: %s\n"
		"FullName: %s\n"
		"FamilyName: %s\n"
		"Weight: Medium\n"
		"Copyright: Created by %s with genfont %s\n"
		"UComments: \"%04d-%d-%d: Created.\" \n"
		"Version: 001.000\n"
		"ItalicAngle: 0\n"
		"UnderlinePosition: -100\n"
		"UnderlineWidth: 50\n"
		"Ascent: %d\n"
		"Descent: %d\n"
		"LayerCount: 2\n"
		"Layer: 0 0 \"Back\"  1\n"
		"Layer: 1 0 \"Fore\"  0\n"
		"XUID: [1021 590 %ld 919824]\n"
		"FSType: 0\n"
		"OS2Version: 0\n"
		"OS2_WeightWidthSlopeOnly: 0\n"
		"OS2_UseTypoMetrics: 1\n"
		"CreationTime: %ld\n"
		"ModificationTime: %ld\n"
		"OS2TypoAscent: 0\n"
		"OS2TypoAOffset: 1\n"
		"OS2TypoDescent: 0\n"
		"OS2TypoDOffset: 1\n"
		"OS2TypoLinegap: 90\n"
		"OS2WinAscent: 0\n"
		"OS2WinAOffset: 1\n"
		"OS2WinDescent: 0\n"
		"OS2WinDOffset: 1\n"
		"HheadAscent: 0\n"
		"HheadAOffset: 1\n"
		"HheadDescent: 0\n"
		"HheadDOffset: 1\n"
		"OS2Vendor: 'PfEd'\n"
		"DEI: 91125\n"
		"Encoding: Custom\n"
		"UnicodeInterp: none\n"
		"NameList: Adobe Glyph List\n"
		"DisplaySize: -24\n"
		"AntiAlias: 1\n"
		"FitToEm: 1\n"
		"WinInfo: 16 16 15\n",
		name, name, name,
		user, arg,
		tm->tm_year + 1900, tm->tm_mon + 1, tm->tm_mday,
		ascent, descent,
		t, t, t);
}

unsigned char hex_digits[] = {
// segs:  .fedcbag
	0b01111110,	// "0"
	0b00001100,	// "1"
	0b00110111,	// "2"
	0b00011111,	// "3"
	0b01001101,	// "4"
	0b01011011,	// "5"
	0b01111011,	// "6"
	0b00001110,	// "7"
	0b01111111,	// "8"
	0b01011111,	// "9"
	0b01101111,	// "A"
	0b01111001,	// "b"
	0b01110010,	// "C"
	0b00111101,	// "d"
	0b01110011,	// "E"
	0b01100011,	// "F"
};

static void genchars() {
	int c;
# if 0
	// Generate spacing chars:
	for (c = 1; c < 10; ++c) {
		int w = ((wid * c / 10.0) + 0.5);
		do_char(-w, c);
	}
#endif
	// Generate display chars:
	for (c = 0; c < 16; ++c) {
		int fc = c + 0x40;
		int b = hex_digits[c];
		do_char(b, fc);
	}
}

int main(int argc, char **argv) {
	int c;
	int x, y;
	char *name = "Untitled";

	extern int optind;
	extern char *optarg;

	while ((x = getopt(argc, argv, "N:")) != EOF) {
		switch(x) {
		case 'N':
			name = optarg;
			break;
		}
	}
	x = optind;

	if (0) {
		fprintf(stderr,
			"Usage: %s [options]\n"
			"Options:\n"
			"         -N name  Name to use in output SFD data\n"
			, argv[0]);
		exit(1);
	}

	preamble(2048, 0, name, argv[x]);

	int max = 0x100 + 32; // assume at least 0x100-0x11f
	int cnt = 256;
	printf("\nBeginChars: %d %d\n\n", max, cnt);

	genchars();
	printf("\nEndChars\n"
		"EndSplineFont\n");

	return 0;
}
