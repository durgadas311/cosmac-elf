JAVAS = $(wildcard *.java)
CLASSES = $(subst .java,.class,$(JAVAS))

all: VirtualCOSMAC_ELF.jar

class: $(CLASSES)

%.class: %.java
	javac $?

VirtualCOSMAC_ELF.jar: $(CLASSES)
	echo "Main-Class: VirtualCOSMAC_ELF" >Manifest.txt
	jar -cmf Manifest.txt $@ *.class *.ttf icons/*.png

clean:
	rm -f *.class
