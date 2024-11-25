/**
 *  Audio
 *  Copyright 2014 by Michael Peter Christen
 *  First released 07.10.2014 at https://yacy.net
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *  
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *  
 *  You should have received a copy of the GNU Lesser General Public License
 *  along with this program in the file lgpl21.txt
 *  If not, see <http://www.gnu.org/licenses/>.
 */

package net.yacy.gui;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.BooleanControl;
import javax.sound.sampled.Clip;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.UnsupportedAudioFileException;

import net.yacy.kelondro.util.MemoryControl;


/**
 * wrapper class for audio tools
 */
public class Audio {

    private final static String path = "htroot/env/soundclips/";
    
    public static enum Soundclip {
        
        dhtin("dhtin.wav"),
        newdoc("newdoc.wav"),
        remotesearch("remotesearch.wav");
        
        public final String filename;
        private Clip clip;
        private AudioFormat aisFormat; // experimental
        private byte[] data; // experimental
        
        private Soundclip(String filename) {
            this.filename = filename;
            this.clip = null;
            this.aisFormat = null;
            this.data = null;
        }

        private Clip getClip() {
            if ("true".equals(System.getProperty("java.awt.headless"))) return null; // don't make noise on headless systems
            if (this.clip != null && this.clip.isOpen()) return this.clip;
            if (this.clip == null || !this.clip.isOpen()) try {
                Thread.sleep(1000); // maybe another thread is opening the clip
                // should be open now! test again...
                if (this.clip != null && this.clip.isOpen()) return this.clip;
            } catch (InterruptedException e1) {}
            // open the clip now
            this.clip = getFreshClip();
            return this.clip;
        }
        
        private Clip getFreshClip() {
            if ("true".equals(System.getProperty("java.awt.headless"))) return null; // don't make noise on headless systems
            // open the clip now
            try {
                AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(new File(path + filename).getAbsoluteFile());
                Clip newclip = AudioSystem.getClip();
                newclip.open(audioInputStream);
                return newclip;
            } catch (UnsupportedAudioFileException e) {
            } catch (IOException e) {
            } catch (LineUnavailableException e) {
            }
            return null;
        }
        
        /**
         * play the soundclip continuously
         */
        public void start() {
            Clip clip;
            if ((clip = getClip()) == null || clip.isActive()) return;
            BooleanControl muteControl = (BooleanControl) clip.getControl(BooleanControl.Type.MUTE);
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            gainControl.setValue(-80.0f);
            clip.loop(Clip.LOOP_CONTINUOUSLY);
            for (float gain = -30.0f; gain < 0.0f; gain += 6.0f) try {
                muteControl.setValue(true);
                gainControl.setValue(gain);
                muteControl.setValue(false);
                Thread.sleep(600);} catch (InterruptedException e) {}
        }
        
        /**
         * stop a continuously playing soundclip
         */
        public void stop() {
            DataLine clip;
            if ((clip = getClip()) == null || !clip.isActive()) return;
            BooleanControl muteControl = (BooleanControl) clip.getControl(BooleanControl.Type.MUTE);
            FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            for (float gain = 0.0f; gain > -30.0f; gain -= 6.0f) try {
                muteControl.setValue(true);
                gainControl.setValue(gain);
                muteControl.setValue(false);
                Thread.sleep(600);} catch (InterruptedException e) {}
            clip.stop();
            clip.flush();
        }
        
        /**
         * play a soundclip once
         */
        public void play(float gain) {
            Clip clip;
            if ((clip = getClip()) == null) return;
            if (clip.isActive()) {
                if (!MemoryControl.shortStatus()) try {
                    Clip onetimeclip = getFreshClip();
                    FloatControl gainControl = (FloatControl) onetimeclip.getControl(FloatControl.Type.MASTER_GAIN);
                    gainControl.setValue(gain);
                    onetimeclip.start();
                } catch (OutOfMemoryError e) {}
            } else {
                FloatControl gainControl = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
                gainControl.setValue(gain);
                clip.setFramePosition(0);
                clip.start();
            }
        }
        
        /**
         * Experimental alternative implementation of play() which reads the sound data and plays it with own methods.
         * This is here because the start/stop methods do a click sound during fade in / fade out.
         * @throws IOException
         */
        public void playExperimental() throws IOException {
            ensureLoaded();
            try {
                SourceDataLine line = AudioSystem.getSourceDataLine(this.aisFormat);
                line.open(this.aisFormat);
                line.start();
                int frameSize = this.aisFormat.getFrameSize();
                for (int i = 0; i < this.data.length - frameSize * 100; i += frameSize * 100) line.write(this.data, i, frameSize * 100);
                line.drain();
                line.close();
            } catch (LineUnavailableException e) {
                throw new IOException(e.getMessage());
            }
        }
        
        private void ensureLoaded() throws IOException {
            if (this.data != null) return;
            try {
                AudioInputStream ais = AudioSystem.getAudioInputStream(new File(path + filename));
                this.aisFormat = ais.getFormat();
                int rc = 0;
                byte[] buffer = new byte[1024];
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                while ((rc = ais.read(buffer, 0, buffer.length)) > 0) {
                    baos.write(buffer, 0, rc);
                }
                this.data = baos.toByteArray();
            } catch (UnsupportedAudioFileException e) {
                throw new IOException(e.getMessage());
            }
        }
    }
   
    public static void main(String[] args) {
        try {
            Soundclip.dhtin.playExperimental();Thread.sleep(100);
            Soundclip.newdoc.play(-20.0f);Thread.sleep(500);
            Soundclip.newdoc.play(-20.0f);Thread.sleep(500);
            Soundclip.newdoc.play(-20.0f);Thread.sleep(500);
            Soundclip.remotesearch.start(); Thread.sleep(1000); Soundclip.remotesearch.stop(); Thread.sleep(1000);
            Soundclip.remotesearch.start(); Thread.sleep(1000); Soundclip.remotesearch.stop(); Thread.sleep(1000);
            Soundclip.remotesearch.start(); Thread.sleep(1000); Soundclip.remotesearch.stop(); Thread.sleep(1000);
        } catch(Exception ex) {
            System.out.println("Error playing sound: " + ex.getMessage());
            ex.printStackTrace();
        }
    }
    
}
