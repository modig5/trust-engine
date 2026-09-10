APP_NAME := trust-engine

SRC_DIR := src
BUILD_DIR := build
CLASSES_DIR := $(BUILD_DIR)/classes
JAR_DIR := $(BUILD_DIR)
JAR := $(JAR_DIR)/$(APP_NAME).jar

MAIN ?= main.Main

JAVA ?= java
JAVAC ?= javac
JAR_TOOL ?= jar

# Override if you need an older/newer JDK target.
JAVA_RELEASE ?= 21
PROFILE_DEPTH ?= 7
PROFILE_RUNS ?= 1
PROFILE_WARMUPS ?= 2

JAVA_SOURCES := $(shell find $(SRC_DIR) -name '*.java')

.PHONY: all compile jar run run-jar uci uci-jar perft profile clean help

all: jar

$(CLASSES_DIR):
	@mkdir -p "$@"

compile: $(CLASSES_DIR)
	$(JAVAC) --release $(JAVA_RELEASE) -d "$(CLASSES_DIR)" $(JAVA_SOURCES)
	@if [ -d "$(SRC_DIR)/resources" ]; then \
		mkdir -p "$(CLASSES_DIR)/resources"; \
		cp -R "$(SRC_DIR)/resources/." "$(CLASSES_DIR)/resources/"; \
	fi

jar: compile
	@mkdir -p "$(JAR_DIR)"
	$(JAR_TOOL) --create --file "$(JAR)" --main-class "$(MAIN)" -C "$(CLASSES_DIR)" .
	@echo "Built $(JAR)"

run: compile
	$(JAVA) -cp "$(CLASSES_DIR)" "$(MAIN)"

run-jar: jar
	$(JAVA) -jar "$(JAR)"

# Keep build messages off the protocol's stdout, even when launched by a GUI.
uci:
	@$(MAKE) --no-print-directory compile >&2
	@$(JAVA) -Djava.awt.headless=true -cp "$(CLASSES_DIR)" uci.UciMain

uci-jar: compile
	$(JAR_TOOL) --create --file "$(JAR_DIR)/$(APP_NAME)-uci.jar" --main-class uci.UciMain -C "$(CLASSES_DIR)" .

perft: compile
	$(JAVA) -cp "$(CLASSES_DIR)" tests.Perft

profile: compile
	$(JAVA) -Xms256m -Xmx512m -Djava.awt.headless=true -cp "$(CLASSES_DIR)" engine.SearchProfile $(PROFILE_DEPTH) $(PROFILE_RUNS) $(PROFILE_WARMUPS)

clean:
	rm -rf "$(BUILD_DIR)"

help:
	@printf "%s\n" \
		"Targets:" \
		"  make            Build jar (default)" \
		"  make compile    Compile to $(CLASSES_DIR)" \
		"  make jar        Build runnable jar at $(JAR)" \
		"  make run        Run from class files" \
		"  make run-jar    Run the jar" \
		"  make uci        Run the headless UCI engine" \
		"  make uci-jar    Build $(JAR_DIR)/$(APP_NAME)-uci.jar" \
		"  make perft      Run perft tests" \
		"  make profile    Profile search CPU, allocations and GC with JFR" \
		"  make clean      Remove build output" \
		"" \
		"Vars (override like: make jar JAVA_RELEASE=17):" \
		"  MAIN, JAVA, JAVAC, JAR_TOOL, JAVA_RELEASE" \
		"  PROFILE_DEPTH=7, PROFILE_RUNS=1, PROFILE_WARMUPS=2"
