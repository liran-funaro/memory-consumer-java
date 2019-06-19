
BIN=bin
SRC=src
JAVA_FILES=$(wildcard ${SRC}/*.java)


MAIN_CLASS=MemoryConsumer
EXEC=memory_consumer.jar

all: ${EXEC}


${EXEC}: ${JAVA_FILES}
	@mkdir -p $(BIN)
	javac -d ${BIN} ${SRC}/*.java
	jar cvfe ${EXEC} ${MAIN_CLASS} -C ${BIN} .


clean:
	rm -rf ${BIN}
	rm -f ${EXEC}
