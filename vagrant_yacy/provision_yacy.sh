#!/bin/bash
sudo pacman-key --init
sudo pacman -Sy --noconfirm archlinux-keyring
sudo pacman-key --refresh-keys
sudo pacman -Syu --noconfirm
sudo pacman -Sy --noconfirm --needed base-devel git python pyalpm

git clone https://github.com/actionless/pikaur.git
python3 pikaur/pikaur.py -S --noconfirm pikaur devtools python-pysocks python-defusedxml
pikaur -S --noconfirm jdk-openjdk ant
pikaur -S --noconfirm imagemagick ruby qt5-tools qt5-doc gperf python  xorg-server-xvfb ghostscript git qt5-svg qt5-xmlpatterns base-devel qt5-location qt5-sensors qt5-webchannel libwebp libxslt libxcomposite gst-plugins-base hyphen hyphen-en hyphen-de woff2 cmake qt5-webkit wkhtmltopdf

cd /opt
git clone --depth 1 https://github.com/yacy/yacy_search_server.git
mv yacy_search_server yacy
cd yacy
ant clean all
chown -R vagrant:vagrant ./
sudo -u vagrant ./startYACY.sh