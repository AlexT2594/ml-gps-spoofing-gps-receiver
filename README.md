# GPS Receiver component of the GPS Spoofing Detection System

This element is responsible for the acquisition of single NMEA phrases. It’s considered to be one of the elements with lowest computation power, thus the workload is minimal. The operations performed by this component are:

1. Acquire raw GPS signal
2. Get the NMEA phrase from the signal
3. Transmit the phrase to the data producer
