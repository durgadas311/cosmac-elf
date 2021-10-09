; Program to display (on pixie) keys pressed on hexkeypad
; requires at least 2K of RAM (uses 1K video buffer at 0400h).

double	equ	1	; use double-lines in video

; video RAM buffer must be aligned...
; Registers:
s$intr	equ	1	; interrupt routine (always R(1))
s$stk	equ	2	; stack are for interrupt
s$main	equ	3	; R(3) = main...
s$scr	equ	4	; scratch-pad area - keypad index, X
s$key	equ	5	; R(5) = h$sub
s$putc	equ	7	; R(7) = putc
C	equ	12	; character pattern from table
V	equ	13	; current video location (init: col(0)+7)
tmp	equ	14
cnt	equ	15

	org	0
;----------------------------------
; initialize
	ldi	scratch.0
	plo	s$scr
	ldi	scratch.1
	phi	s$scr
	sex	s$scr
	ldi	h$sub.0
	plo	s$key
	ldi	h$sub.1
	phi	s$key
	ldi	putc.0
	plo	s$putc
	ldi	putc.1
	phi	s$putc
	ldi	intr.0
	plo	s$intr
	ldi	intr.1
	phi	s$intr
	ldi	(video+7).0
	plo	V
	ldi	(video+7).1
	phi	V
	; transfer control to main (new PC)
	ldi	main.0
	plo	s$main
	ldi	main.1
	phi	s$main
	sep	s$main
;
; video refresh interrupt
r$intr:	ldxa
	ret
intr:	dec	s$stk
	sav
	dec	s$stk
	str	s$stk
	nop
	nop
	nop
	ldi	video.1
	phi	0
	ldi	video.0
	plo	0
	br	r$intr

; Get digit from hex keypad.
; Returns D = digit.
; Changes X, uses M(R(2)) and R(4)
h$ret:	ldx		; D = M(R(X)) (digit)
	sep	s$main	; return to main
h$sub:	ldx
	adi	1
	ani	0fh
	str	s$scr
	out	2	; keypad index
	dec	s$scr	; adjust for OUT
	bn2	h$sub	; loop until key pressed
	seq
	ldi	9
	phi	tmp	; 9*256
delay:	dec	tmp
	ghi	tmp
	bnz	delay	; debounce 2048 loops
	b2	$.0	; wait for key released
	req
	br	h$ret	; return

r$putc:	sep	s$main	; back to main
putc:	ghi	V
	phi	tmp	; tmp = V

	; advance to next location...
	inc	V	; ++V
	glo	V
	ani	07h
	bnz	skip
	glo	V
	smi	8
	plo	V
	; clear screen line
	plo	tmp
 if double
	ldi	128
 else
	ldi	64
 endi
	plo	cnt
clear:	ldi	0
	str	tmp
	inc	tmp
	dec	cnt
	glo	cnt
	bnz	clear
skip:
	glo	V
	plo	tmp	; tmp = V
	ldi	8
	plo	cnt
copy:
 if double
	ldn	C	; D = M(R(C))
	str	tmp
	glo	tmp
	adi	8
	plo	tmp	; tmp += 8
 endi
	lda	C	; D = M(R(C)++)
	str	tmp
	glo	tmp
	adi	8
	plo	tmp	; tmp += 8
	dec	cnt
	glo	cnt
	bnz	copy
	br	r$putc

main:
	; setup video...
	inp	1	; enable video

loop:	sep	s$key	; call b$sub
	; D = key (0-15)
	shl
	shl
	shl	; D *= 8
	adi	hextbl.0
	plo	C
	ldi	hextbl.1
	adci	0	; C = &hextbl[key]
	phi	C
	; R(C) = character pattern
	sep	s$putc
	br	loop

; The character generator:
hextbl:
	; "0"
	db	01110000b
	db	10001000b
	db	10011000b
	db	10101000b
	db	11001000b
	db	10001000b
	db	01110000b
	db	0
	; "1"
	db	00100000b
	db	01100000b
	db	00100000b
	db	00100000b
	db	00100000b
	db	00100000b
	db	01110000b
	db	0
	; "2"
	db	01110000b
	db	10001000b
	db	00001000b
	db	01110000b
	db	10000000b
	db	10000000b
	db	11111000b
	db	0
	; "3"
	db	01110000b
	db	10001000b
	db	00001000b
	db	01110000b
	db	00001000b
	db	10001000b
	db	01110000b
	db	0
	; "4"
	db	00011000b
	db	00101000b
	db	01001000b
	db	11111000b
	db	00001000b
	db	00001000b
	db	00001000b
	db	0
	; "5"
	db	11111000b
	db	10000000b
	db	10000000b
	db	11110000b
	db	00001000b
	db	10001000b
	db	01110000b
	db	0
	; "6"
	db	00110000b
	db	01000000b
	db	10000000b
	db	11110000b
	db	10001000b
	db	10001000b
	db	01110000b
	db	0
	; "7"
	db	11111000b
	db	00001000b
	db	00001000b
	db	00010000b
	db	00100000b
	db	01000000b
	db	10000000b
	db	0
	; "8"
	db	01110000b
	db	10001000b
	db	10001000b
	db	01110000b
	db	10001000b
	db	10001000b
	db	01110000b
	db	0
	; "9"
	db	01110000b
	db	10001000b
	db	10001000b
	db	01111000b
	db	00001000b
	db	00001000b
	db	01110000b
	db	0
	; "A"
	db	01110000b
	db	10001000b
	db	10001000b
	db	11111000b
	db	10001000b
	db	10001000b
	db	10001000b
	db	0
	; "B"
	db	11110000b
	db	10001000b
	db	10001000b
	db	11110000b
	db	10001000b
	db	10001000b
	db	11110000b
	db	0
	; "C"
	db	01110000b
	db	10001000b
	db	10000000b
	db	10000000b
	db	10000000b
	db	10001000b
	db	01110000b
	db	0
	; "D"
	db	11110000b
	db	10001000b
	db	10001000b
	db	10001000b
	db	10001000b
	db	10001000b
	db	11110000b
	db	0
	; "E"
	db	11111000b
	db	10000000b
	db	10000000b
	db	11110000b
	db	10000000b
	db	10000000b
	db	11111000b
	db	0
	; "F"
	db	11111000b
	db	10000000b
	db	10000000b
	db	11110000b
	db	10000000b
	db	10000000b
	db	10000000b
	db	0

	db	0,0,0,0
stack:	db	0
scratch:	db	0

video	equ	0400h

	end
