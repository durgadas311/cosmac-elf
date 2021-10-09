; Program to display (on pixie) memory, byte-by-byte.
; requires at least 2K of RAM (uses 1K video buffer at 0400h).

; Operation:
; starts by displaying location 0000 address and contents.
; Press IN with toggle 7 off to step +1.
; Press IN with toggle 7 on to step -1.
; Press a hex key with toggle 0 on to change address
; Press a hex key with toggle 0 off to alter data
; Press IN with toggle 1 on to GOTO current address

double	equ	1	; use double-lines in video
hexinp	equ	1	; use hex keypad for data input, else only IN +/- step

; video RAM buffer must be aligned...
; Registers:
s$intr	equ	1	; interrupt routine (always R(1))
s$stk	equ	2	; stack area for interrupt
s$main	equ	3	; R(3) = main...
s$scr	equ	4	; scratch-pad area - keypad index, X
s$key	equ	5	; R(5) = keypd
s$ptr	equ	6	; mem ptr
s$putc	equ	7	; R(7) = putc
s$nib	equ	8	; R(8) = nibble
s$byt	equ	9	; R(9) = byte
s$clr	equ	11	; R(11) = clear
C	equ	12	; nibble/putc: character pattern from table
V	equ	13	; global: current video location ("cursor") for putc
tmp	equ	14	; clear/putc: temp video buf ptr
cnt	equ	15	; R(cnt).0: clear/putc: should not disturb R(cnt).1
			; R(cnt).1: byte: to save value between nibble

	org	0
;----------------------------------
; initialize
	ldi	scratch.0
	plo	s$scr
	ldi	scratch.1
	phi	s$scr
	sex	s$scr
	ldi	keypd.0
	plo	s$key
	ldi	keypd.1
	phi	s$key
	ldi	putc.0
	plo	s$putc
	ldi	putc.1
	phi	s$putc
	ldi	nibble.0
	plo	s$nib
	ldi	nibble.1
	phi	s$nib
	ldi	byte.0
	plo	s$byt
	ldi	byte.1
	phi	s$byt
	ldi	clear.0
	plo	s$clr
	ldi	clear.1
	phi	s$clr
	ldi	intr.0
	plo	s$intr
	ldi	intr.1
	phi	s$intr
	ldi	video.0
	plo	V
	ldi	video.1
	phi	V	; V = video
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

; Get digit from hex keypad, or IN button press.
; Returns D = digit or Q=1 if IN pressed.
; Assume X=2=scratch, uses M(R(X)) and R(tmp)
; NOTE: starting value in M(R(X)) does not matter.
r$kpd:	sep	s$main	; return to main
keypd:	b4	keypd1	; IN pressed instead...
	ldx
	adi	1
	ani	0fh
	str	s$scr
	out	2	; keypad index
	dec	s$scr	; adjust for OUT
	bn2	keypd	; loop until key pressed
	seq		; visual indicator (key click?)
	ldi	9
	phi	tmp	; 9*256 (tmp.0 may have residual?)
keypd0:	dec	tmp
	ghi	tmp
	bnz	keypd0	; debounce 2048 loops
	;req		; for key click, need short blip
	b2	$.0	; wait for key released
	req		; for visual indicator...
	ldx		; get keypd index, 0-15
	br	r$kpd	; return
keypd1:	b4	$.0	; wait for release
	seq		; signal IN pressed
	br	r$kpd	; return

; clear screen line
r$clr:	sep	s$main	; only called from main
clear:
	glo	V
	plo	tmp
	ghi	V
	phi	tmp	; tmp = V
 if double
	ldi	128
 else
	ldi	64
 endi
	plo	cnt
clear0:	ldi	0
	str	tmp
	inc	tmp
	dec	cnt
	glo	cnt
	bnz	clear0
	br	r$clr

r$putc:	inc	V	; next location (caller checks overflow)
	sep	s$nib	; only called from 'nibble'
putc:	ghi	V
	phi	tmp	; tmp = V
	glo	V
	plo	tmp	; tmp = V
	ldi	8
	plo	cnt
putc0:
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
	bnz	putc0
	br	r$putc

; Display hex digit (nibble) on screen at V
; Only called from 'byte'
r$nib:	sep	s$byt
nibble:
	ani	0fh
	; D = nibble (0-15)
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
	br	r$nib

r$byt:	sep	s$main	; only called from main
byte:	; D = byte
	phi	cnt	; safe?
	shr
	shr
	shr
	shr
	sep	s$nib
	ghi	cnt
	sep	s$nib
	br	r$byt

main:
	; setup video...
	inp	1	; enable video
	; TODO: enter address from hex keypad
	ldi	0
	plo	s$ptr
	phi	s$ptr
loop:
	; display R(ptr) and M(R(ptr)) on screen...
	ghi	s$ptr
	sep	s$byt
	glo	s$ptr
	sep	s$byt
	inc	V	; leave blank space
	ldn	s$ptr
	sep	s$byt
	ldi	video.0
	plo	V	; effective CR
 if hexinp
	sep	s$key	; call keypd (or IN)
	bq	input	; must REQ...
	; else M(R(X)) contains key...
	inc	s$scr
	inp	4	; get switches - destroys M(R(X))!
	dec	s$scr
	shrc		; test switch 0
	bdf	doadr
	; update M(R(ptr)) data
	ldn	s$ptr
	shl
	shl
	shl
	shl
	or	; D |= M(R(X))
	str	s$ptr
	br	loop
doadr:
	; shift R(ptr) << 4
	ldi	4
	plo	cnt
doadr0:	glo	s$ptr
	shl
	plo	s$ptr
	ghi	s$ptr
	shlc		; carry from ptr.0
	phi	s$ptr
	dec	cnt
	glo	cnt
	bnz	doadr0
	glo	s$ptr	; now merge key
	or	; D |= M(R(X))
	plo	s$ptr
	br	loop

input:	req
 else
	bn4	$.0	; wait for IN
	b4	$.0	; wait for release
 endi
	inp	4	; get switches - destroys M(R(X))!
	shlc		; switch 7 "on"?
	bdf	back
	ani	00000100b	; switch 1 in new position
	bnz	goto
	; forward step...
	inc	s$ptr
	br	loop
back:	dec	s$ptr
	br	loop
goto:	; transition to temp PC, then setup s$main
	ldi	goto0.0
	plo	s$scr
	ldi	goto0.1
	phi	s$scr
	sep	s$scr
	page	; we're too close to end of page 0...
goto0:	glo	s$ptr
	plo	s$main
	ghi	s$ptr
	phi	s$main
	sep	s$main	; goto R(ptr), PC=3

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
	db	11110000b
	db	00001000b
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

ram	equ	0200h	; above code must not overflow...
stack	equ	ram + 4	; 4 locations *prior* to this, 1 after (buffer)
scratch	equ	stack + 1	; 2 locations...
;next	equ	scratch + 2...

video	equ	0400h

	end
