#!/bin/bash

echo " Pornim sistemul de licitație Okazii..."

# 0. Curățăm jurnalele vechi
rm -f *_journal*.txt

# Stabilim calea relativă față de rădăcina proiectului
# Dacă folderul artifacts are alt nume, modifică doar aici
BASE_DIR="./out/artifacts"

# 1. Pornim HeartbeatMicroservice (PAZNICUL) - Port 1800
echo " Pornim HeartbeatMicroservice..."
#java -jar "$BASE_DIR/HeartbeatMicroservice_jar/HeartbeatMicroservice.jar" &
#sleep 1

# 2. Pornim backend-ul (Procesoarele)
echo "1️ Pornim MessageProcessor si BiddingProcessor..."
java -jar "$BASE_DIR/MessageProcessorMicroservice_jar/MessageProcessorMicroservice.jar" &
java -jar "$BASE_DIR/BiddingProcessorMicroservice_jar/BiddingProcessorMicroservice.jar" &
sleep 2

# 3. Pornim Auctioneer-ul (Incepe cronometrul de 15 secunde!)
echo "2️ Pornim AuctioneerMicroservice..."
java -jar "$BASE_DIR/AuctioneerMicroservice_jar/AuctioneerMicroservice.jar" &
sleep 1

# 4. Pornim clienții (Bidderii)
NUMAR_BIDDERI=15
echo "3️ Pornim $NUMAR_BIDDERI Bidderi..."

for i in $(seq 1 $NUMAR_BIDDERI)
do
   java -jar "$BASE_DIR/BidderMicroservice_jar/BidderMicroservice.jar" &
done

echo " Asteptam finalizarea licitatiei..."
wait

echo " Licitatia s-a incheiat cu succes!"