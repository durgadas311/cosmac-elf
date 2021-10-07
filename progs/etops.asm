; ETOPS-256
; "monitor" program, for entering and running programs,
; using toggle switches and IN button.
;
; User sets switches for mode... 00, 01, XX
; User starts this program (RUN).
; Mode is displayed, waits for IN...
;
; 00000000 = GO: wait for IN, read address, jump to address.
; xxxxxxx1 = VIEW: wait for IN, read address, show bytes at address...
; yyyyyyy0 = STORE: wait for IN, read address, input bytes to address...
; (x = don't care)
; (y = at least one switch "1")
;
; mode is diplayed when starting, Q LED "on" when writing to memory.

	org	0
	ldi	work.0
	plo	1	; R(1) = work
	sex	1	; X=1
	inp	4	; M(R(X)) = D = switches: mode
	out	4	; show current mode value
	dec	1	; correct for OUT
	; user sets switches for address, presses IN...
	bn4	$.0	; Wait for IN on
	b4	$.0	; Wait for IN off
	bz	go	; GO command
	shr		;
	bdf	view	; VIEW command
	seq		; Q=1 - writing to memory
view:	inp	4	; D = toggles
	plo	1	; R(1) = address to put/view data (program)
loop:	bn4	$.0	; Wait for IN on
	b4	$.0	; Wait for IN off
	bnq	view1	; if Q=0, skip new data
	inp	4	; M(R(X)) = toggles
view1:	out	4	; Show M(R(X)++)
	br	loop	; Repeat

go:	inp	4	; D = toggles (also M(R(X)))
	plo	3	; R(3) = address
	sep	3	; P=3, jump to M(R(3))
	; end of 32-byte PROM
work:	;ds	1	; Work area (destroyed)

; User programs from
; M(21) to M(FF)

	end
