
BIN=bin
SRC=SRC
JAVA_FILES=$(wildcard ${SRC}/*.java)


MAIN_CLASS=MemoryConsumer
EXEC=memory_consumer.jar

all: ${EXEC}


${EXEC}: ${JAVA_FILES}
	javac -d ./${BIN} ${SRC}/*.java
	jar cvfe ${EXEC} ${MAIN_CLASS} -C ${BIN} .


install: ${EXEC}
	cp ${EXEC} /usr/local/bin/


clean:
	rm -rf ${BIN}
	rm -f ${EXEC}