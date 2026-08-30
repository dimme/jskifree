# JSkiFree.
#
#   make          build bin/ and skifree.jar
#   make run      build and play (silent)
#   make sound    build and play with sound
#   make clean

JAVAC   ?= javac
JAVA    ?= java
JAR     ?= jar

SRCDIR  = src
BINDIR  = bin
JARFILE = skifree.jar

SOURCES = $(wildcard $(SRCDIR)/skifree/*.java)

all: $(JARFILE)

$(BINDIR)/.classes: $(SOURCES)
	mkdir -p $(BINDIR)
	$(JAVAC) -d $(BINDIR) $(SOURCES)
	touch $@

$(BINDIR)/.resources: $(shell find $(SRCDIR)/resources -type f)
	mkdir -p $(BINDIR)
	rm -rf $(BINDIR)/resources
	cp -r $(SRCDIR)/resources $(BINDIR)/resources
	touch $@

$(JARFILE): $(BINDIR)/.classes $(BINDIR)/.resources
	$(JAR) --create --file $@ --main-class skifree.JSkiFree -C $(BINDIR) skifree -C $(BINDIR) resources

run: $(JARFILE)
	$(JAVA) -jar $(JARFILE)

sound: $(JARFILE)
	$(JAVA) -jar $(JARFILE) sound

clean:
	rm -rf $(BINDIR) $(JARFILE)

.PHONY: all run sound clean
