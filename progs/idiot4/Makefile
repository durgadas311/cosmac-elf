# This makefile depends on the 1802 cross-assembler "a18"
# from https://www.retrotechnology.com/memship/a18.html

AS = a18

ASMS = $(wildcard idiotX*.asm)
BINS = $(subst .asm,.bin,$(ASMS))

all: $(BINS)

%.bin: %.asm
	$(AS) $< -l $*.lst -b $@
