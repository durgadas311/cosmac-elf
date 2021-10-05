	org	0

	ghi	0	; current PC hi address
	phi	1
	phi	2
	phi	3
	phi	4
	ldi	main.0
	plo	3	; R(3) = main (next PC)
	ldi	stack.0
	plo	2	; R(2) = stack
	ldi	intr.0
	plo	1	; R(1) = intr
	sep	3	; switch P, jump to main
; ---------------------
return:
	ldxa
	; must precede entry ('intr') so
	; PC is correct for next interrupt
	ret
intr:
	dec	2
	sav
	dec	2
	str	2
	nop
	nop
	nop
	ldi	0
	phi	0
	ldi	0
	plo	0	; refresh ptr = 0000 (R(0))
refresh:
; This is for 64x32, may not work on simulator
 if 1
	glo	0
	sex	2
	; 8 DMA cycles (R(0) += 8)
	sex	2
	dec	0
	plo	0
	; 8 DMA cycles (R(0) += 8)
	sex	2
	dec	0
	plo	0
	; 8 DMA cycles (R(0) += 8)
	sex	2
	dec	0
	plo	0
	; 8 DMA cycles (R(0) += 8)
	bn1	refresh
 endi
	br	return
; ---------------------
main:
	sex	2
	inp	1	; enable display
	; wait for entry of address...
	bn4	$.0	; wait for IN
	inp	4
	plo	4
more:
	b4	$.0	; wait for release
	bn4	$.0	; wait for IN (data)
	inp	4
	str	4
	inc	4
	br	more

	db	0,0,0
stack:

	org	($ + 7) and 0fff8h	; next CRT scanline

 if 0	; simple "hello"
	db	0,0,0,0,0,0,0,0
	db	0,0,0,0,0,0,0,0
	db	10010000b,11110000b,10000000b,10000000b,01100000b,0,0,0
	db	10010000b,11110000b,10000000b,10000000b,01100000b,0,0,0
	db	10010000b,10000000b,10000000b,10000000b,10010000b,0,0,0
	db	10010000b,10000000b,10000000b,10000000b,10010000b,0,0,0
	db	11110000b,11110000b,10000000b,10000000b,10010000b,0,0,0
	db	11110000b,11110000b,10000000b,10000000b,10010000b,0,0,0
	db	10010000b,10000000b,10000000b,10000000b,10010000b,0,0,0
	db	10010000b,10000000b,10000000b,10000000b,10010000b,0,0,0
	db	10010000b,11110000b,11110000b,11110000b,01100000b,0,0,0
	db	10010000b,11110000b,11110000b,11110000b,01100000b,0,0,0
 endi
 if 1	; classic "spaceship"
	db	0,0,0,0,0,0,0,0
	db	0,0,0,0,0,0,0,0
	db	7Bh,0DEh,0DBh,0DEh,000h,000h,000h,000h
	db	4Ah,050h,0DAh,052h,000h,000h,000h,000h
	db	42h,05Eh,0ABh,0D0h,000h,000h,000h,000h
	db	4Ah,042h,08Ah,052h,000h,000h,000h,000h
	db	7Bh,0DEh,08Ah,05Eh,000h,000h,000h,000h
	db	0,0,0,0,0,0,0,0
	db	0,0,0,0,0,0,7,0e0h
	db	00h,000h,000h,000h,0FFh,0FFh,0FFh,0FFh
	db	00h,006h,000h,001h,000h,000h,000h,001h
	db	00h,07Fh,0E0h,001h,000h,000h,000h,002h
	db	7Fh,0C0h,03Fh,0E0h,0FCh,0FFh,0FFh,0FEh
	db	40h,00Fh,000h,010h,004h,080h,000h,000h
	db	7Fh,0C0h,03Fh,0E0h,004h,080h,000h,000h
	db	00h,03Fh,0D0h,040h,004h,080h,000h,000h
	db	00h,00Fh,008h,020h,004h,080h,07Ah,01Eh
	db	00h,000h,007h,090h,004h,080h,042h,010h
	db	00h,000h,018h,07Fh,0FCh,0F0h,072h,01Ch
	db	00h,000h,030h,000h,000h,010h,042h,010h
	db	00h,000h,073h,0FCh,000h,010h,07Bh,0D0h
	db	00h,000h,030h,000h,03Fh,0F0h,000h,000h
	db	00h,000h,018h,00Fh,0C0h,000h,000h,000h
	db	00h,000h,007h,0F0h,000h,000h,000h,000h
 endi

	end
