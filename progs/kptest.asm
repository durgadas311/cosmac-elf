; Program to test Hex Keypad.
; Scan for key pressed, display value, wait for key release, repeat.
;
count	equ	32	; someplace beyond ROM

	org	0
start:
	sex	5	; use R5 for X
	ldi	count.0
	plo	5	; set R(X) = count
loop:
	out	2	; N1 = index
	dec	5	; correct for OUT
	bn2	miss
	out	4	; N2 = display
	dec	5	; correct for OUT
wait:	b2	wait	; wait for key-up
miss:	ldi	1	; increment M(R(X))...
	add		; +1
	ani	0fh	; keep it 0-15
	str	5	; save in M(R(X))
	br	loop	; keep going

	end
