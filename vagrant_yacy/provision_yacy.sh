#!/bin/bash
sudo pacman-key --init
sudo pacman -Sy --noconfirm archlinux-keyring
sudo pacman-key --refresh-keys
sudo pacman -Syu --noconfirm
sudo pacman -Sy --noconfirm --needed base-devel git python pyalpm

git clone https://github.com/actionless/pikaur.git
python3 pikaur/pikaur.py -S --noconfirm pikaur devtools python-pysocks python-defusedxml
pikaur -S --noconfirm jdk-openjdk ant

cd /opt
git clone --depth 1 https://github.com/yacy/yacy_search_server.git
mv yacy_search_server yacy
cd yacy
ant clean all
chown -R vagrant:vagrant ./
sudo -u vagrant ./startYACY.sh