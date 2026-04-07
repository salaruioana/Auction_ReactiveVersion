# Okazii - Reactive Auction Microservices
Disclaimer: The base code for this project was provided as part of the Distributed Systems laboratory, while this repository contains my personal modifications, extensions, and architectural improvements.

This project is a distributed application developed in **Kotlin** that models an online auction system (similar to eBay/Okazii). The architecture is based on **reactive microservices**, with communication handled via TCP sockets and asynchronous data streams managed through **RxKotlin**.

## System Architecture

The system is divided into 5 distinct modules:

* **MessageLibrary**: Core module containing the message structure and serialization/deserialization functions.
* **AuctioneerMicroservice**: The central point of the auction. It collects all bids within a 15-second window and forwards them for processing.
* **BidderMicroservice**: Models the auction participants. Each instance sends a random bid and waits for the result.
* **MessageProcessorMicroservice**: Intercepts the bids, removes duplicates, and sorts them chronologically before forwarding them.
* **BiddingProcessorMicroservice**: Analyzes the final bids, determines the winner (the highest bid), and sends the result back to the Auctioneer.

##  Technologies Used

* **Language:** Kotlin (JVM)
* **Reactive Programming:** RxKotlin (Observables, Subscriptions)
* **Networking:** Java Sockets (TCP)

## Current Status (Version 1.0)

The **Laboratory Requirements** have been successfully implemented and tested:
- [x] Successful connection and execution of the base application.
- [x] Simulation of 100+ concurrent `Bidder` instances.
- [x] Filtering duplicate bids using reactive operators in the `MessageProcessor`.
- [x] Sorting messages based on timestamp upon stream completion.
- [x] Improved the implementation so that each user is identified by a name, phone number and an email address.

**Up Next (Homework Assignment):**
- [ ] Fault tolerance mechanism (Write-Ahead Logging for current operational state).
- [ ] Heartbeat microservice (Monitoring Docker instances and automatic restart on failure).
- [ ] Generalized log collection system (instrumentation and log centralization).

## How to Run

Because these are independent microservices communicating via specific ports, the startup order is crucial:
1. `BiddingProcessorMicroservice` (Port 1700)
2. `MessageProcessorMicroservice` (Port 1600)
3. `AuctioneerMicroservice` (Port 1500)
4. `BidderMicroservice` instances (these can be started manually or via a bash script for massive concurrent execution).
