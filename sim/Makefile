JAVAS = $(wildcard *.java)
CLASSES = $(subst .java,.class,$(JAVAS))
DOCS = $(filter-out docs/About.html,$(wildcard docs/*))
ICONS = $(wildcard icons/*.png)
FONTS = $(wildcard *.ttf)
SUPPS = $(DOCS) $(ICONS) $(FONTS)

all: VirtualCOSMAC_ELF.jar

class: $(CLASSES)

%.class: %.java
	javac $?

VirtualCOSMAC_ELF.jar: $(CLASSES) $(SUPPS)
	./mkAbout >docs/About.html
	echo "Main-Class: VirtualCOSMAC_ELF" >Manifest.txt
	jar -cmf Manifest.txt $@ *.class *.ttf \
		icons/*.png docs/*.html docs/*.png

clean:
	rm -f *.class
