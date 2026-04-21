#!/bin/bash

echo " Pornim sistemul de licitație Okazii cu Jurnalizare Reactivă..."

# 0. Curățăm jurnalele vechi (locale și cel general)
rm -f *_journal*.txt
rm -f master_general_journal.log

BASE_DIR="./out/artifacts"

# 1. PORNEȘTE MASTER LOGGER (Trebuie să fie gata să primească conexiuni)
echo "0️ Pornim MasterLoggerMicroservice..."
java -jar "$BASE_DIR/MasterLoggerMicroservice_jar/MasterLoggerMicroservice.jar" &
sleep 2 # Îi dăm un pic de timp să deschidă socket-ul pe 1900

# 2. Pornim HeartbeatMicroservice (Paznicul)
echo "1️ Pornim HeartbeatMicroservice..."
java -jar "$BASE_DIR/HeartbeatMicroservice_jar/HeartbeatMicroservice.jar" &
HEARTBEAT_PID=$!
sleep 1

# 3. Pornim backend-ul (Procesoarele)
echo "2️ Pornim MessageProcessor si BiddingProcessor..."
java -jar "$BASE_DIR/MessageProcessorMicroservice_jar/MessageProcessorMicroservice.jar" &
java -jar "$BASE_DIR/BiddingProcessorMicroservice_jar/BiddingProcessorMicroservice.jar" &
sleep 1

# 4. Pornim Auctioneer-ul
echo "3️ Pornim AuctioneerMicroservice..."
java -jar "$BASE_DIR/AuctioneerMicroservice_jar/AuctioneerMicroservice.jar" &
AUCTIONEER_PID=$!
sleep 1

# 5. Pornim Bidderii cu ID-uri unice
NUMAR_BIDDERI=15
echo "4️ Pornim $NUMAR_BIDDERI Bidderi cu ID-uri pentru monitorizare..."

for i in $(seq 1 $NUMAR_BIDDERI)
do
   # Trimitem $i ca argument pentru ID unic și Recovery
   java -jar "$BASE_DIR/BidderMicroservice_jar/BidderMicroservice.jar" $i &
done

echo " Sistemul este complet pornit. Verifică master_general_journal.log pentru activitate."
wait $AUCTIONEER_PID

# Dupa ce Auctioneer s-a inchis (licitatia e gata), oprim Heartbeat-ul
echo " Licitatia s-a incheiat cu succes! Oprim Heartbeat-ul..."
kill $HEARTBEAT_PID

# (Optional) Ne asiguram ca nu ramane vreun Bidder agatat
pkill -f BidderMicroservice

echo " Toate procesele au fost oprite curat."