#!/bin/sh
mkdir -p out
javac -d out -sourcepath src src/triqueue/Main.java src/triqueue/ui/MainGUI.java && java -cp out triqueue.ui.MainGUI