# R8 for the release build. Activities, services, receivers and the views named in layouts are kept by
# the rules the build writes itself, and the libraries bring their own; nothing here needs keeping by hand.

# Crash reports keep their line numbers; the file names are folded into one
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
