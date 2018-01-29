#!/bin/bash

socat UDP4-RECVFROM:49707,ip-add-membership=239.255.1.1:0.0.0.0,fork - |hexdump -C
