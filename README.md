# Kiany-SD (Bus WiFi Monitor) 🚌📱

A lightweight, background-monitoring Android application built with **Jetpack Compose** and modern Android architecture. It addresses a highly specific, local daily struggle: remembering to validate your bus ticket before a ticket inspector boards.

---

## 🧐 The Backstory (Why this exists)
Let's face it: riding the "Kavim" bus line can be a journey. You board the bus, you find a seat, and your mind drifts away. Suddenly, you realize you haven't validated your ride via the ticketing app, or worse—the inspectors are already checking cards. 

This app acts as your automated safety net. It runs silently in the background, keeping an eye out for specific onboard bus WiFi networks, ensuring you are **always alerted** to pay before it's too late. 

Because as the official logo says:
<p align="center">
  <img src="pixil-frame-0.png" alt="Kiany-SD Official Logo" width="200"/>
</p>

---

## 🚀 Key Features

* **Background Network Monitoring:** Uses Android's native network connectivity APIs to detect when you connect to or are near specific transit WiFi networks.
* **Persistent Reminders:** Sends custom notifications to ensure you don't forget to validate your ride.
* **Seamless Automation:** Designed to launch or redirect you to your preferred ticketing app instantly.
* **Local Preferences Storage:** Uses `SharedPreferences` to remember state and user configurations efficiently without heavy database overhead.
* **Modern UI:** Built entirely using **Jetpack Compose** with a clean, responsive layout.

---

## 🛠️ Architecture & Tech Stack

This project is built using modern Android development (MAD) practices:
* **Language:** Kotlin 
* **UI Framework:** Jetpack Compose (Declarative UI)
* **Background Processing:** Android Service / ConnectivityManager for efficient, low-battery network polling.
* **Data Storage:** SharedPreferences for simple local persistence.
* **Version Control:** Git managed seamlessly through Android Studio.

---

## 📦 How to Install

1. Go to the **Releases** section of this repository.
2. Download the latest `Kiany-SD.apk`. 
3. Transfer it to your Android device and install it (make sure "Install from Unknown Sources" is enabled in your settings).
4. Launch the app, grant the required network permissions, and let it guard your wallet!

---

## ⚠️ Disclaimer & Non-Liability
* **Independent Tool:** This app is an independent helper tool and is not affiliated with, endorsed by, or associated with any official public transportation company.
* **No Warranty (As-Is):** This application is provided "as is" without any warranties of any kind. The developer does not guarantee that the background monitoring will work 100% of the time, as Android systems may terminate background services due to battery optimization, OS updates, or hardware limitations.
* **User Responsibility:** **The developer is NOT responsible or liable** for any fines, tickets, or legal issues you may incur if the app fails to trigger a notification, if you miss a validation, or if the application does not function as expected. Always validate your rides legally and manually check your ticketing status! 😉
