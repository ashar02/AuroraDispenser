#bash

openssl pkcs12 -export -in combine.pem -inkey key.pem -out certificate.p12 -name "certificate"
#cbm123456
keytool -importkeystore -srckeystore certificate.p12 -srcstoretype pkcs12 -destkeystore cert.jks
