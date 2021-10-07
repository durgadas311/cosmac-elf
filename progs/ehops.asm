; EHOPS-256
; Monitor program using hex keypad for data input.
; Enter mode in switches, start program.
; Enter address on hex keypad.

; 00000000 = GO: jump to address.
; xxxxxxx1 = VIEW: wait for any two digits, show bytes at address...
; yyyyyyy0 = STORE: wait for two digits, put bytes to address...
; (x = don't care)
; (y = at least one switch "1")

	org	0
	ldi	work.0
	plo	2	; R(2) = work
	ldi	b$sub.0
	plo	5	; R(5) = b$sub
	ldi	h$sub.0
	plo	6	; R(6) = h$sub
	ldi	main
	plo	3	; R(3) = main
	sep	3	; jump to R(3)
main:
	sep	5	; call R(5) [b$sub]
	plo	1	; R(1) = b$sub();
	inp	4	; 
	bnz	main1
	; 00000000 = GO
go:	glo	1
	plo	3	; R(3) = R(1): jump R(1)

main1:	shr
	bnf	store
	; xxxxxxx1 = VIEW
view:	sep	5	; call b$sub
	sex	1	;
	out	4	; show address
	br	view

	; yyyyyyy0 = STORE
store:	sep	5	; call b$sub
	sex	1
	str	1
	out	4
	br	store

; Get byte (two digits) from hex keypad.
; Returns D = byte, also M(R(2)) = byte, and displayed.
; Uses R(0).0, calls h$sub
b$ret:	sep	3	; return to main
b$sub:	sep	6	; call h$sub
	shl
	shl
	shl
	shl
	plo	0	; save high digit
	sep	6	; call h$sub
	glo	0	; D = high digit
	or		; D |= M(R(X))
	str	2	; M(R(2)) = byte entered
	out	4	; show byte
	dec	2	; adjust for OUT
	br	b$ret	; return

; Get digit from hex keypad.
; Returns D = digit.
; Changes X, uses M(R(2)) and R(4)
h$ret:	ldx		; D = M(R(X)) (digit)
	sep	5	; return to b$sub
h$sub:	sex	2
	adi	1
	ani	0fh
	str	2
	out	2	; keypad index
	dec	2	; adjust for OUT
	bn2	h$sub	; loop until key pressed
	seq
	ldi	9
	phi	4
delay:	dec	4
	ghi	4
	bnz	delay	; debounce 2048 loops
	req
	b2	$.0	; wait for key released
	br	h$ret	; return

work	equ	0ffh	; user must enter address anyway

; User programs from
; M(4A) to M(FF)

	end
