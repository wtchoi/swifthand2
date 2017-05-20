#!/usr/bin/expect
set inputfile [lindex $argv 0];
spawn jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore swifthand.keystore $inputfile swifthandkey
expect "Enter Passphrase for keystore:"
send "swifthandKeystorePass\n"
sleep 1
expect "Enter key password for swifthandkey:"
send "swifthandKeyPass\n"
expect eof
