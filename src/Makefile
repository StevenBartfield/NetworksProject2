JAVAC = javac
JFLAGS = -g -d . -classpath .

SRCS = Server.java Utils.java HTTPRequest.java ResourceMap.java

default: all

all: Server

Server: $(SRCS)
	$(JAVAC) $(JFLAGS) $(SRCS)

clean:
	rm -f *.class


