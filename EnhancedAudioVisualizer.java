import javax.swing.*;
import java.awt.*;
import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class EnhancedAudioVisualizer {
    private static final int SAMPLE_RATE = 44100; // 44.1 kHz
    private static final int BARS = 512; // Number of FFT frequency bins
    private static final int TIMER_INTERVAL_MS = 1; // Update every millisecond

    private JFrame frame;
    private JPanel visualizerPanel;
    private JButton selectFileButton, playButton, stopButton;
    private AudioInputStream audioInputStream;
    private SourceDataLine sourceLine;
    private Timer updateTimer;
    private byte[] audioData;
    private boolean isPlaying = false;
    private File selectedAudioFile; // Store the selected audio file

    public EnhancedAudioVisualizer() {
        frame = new JFrame("Enhanced Audio Visualizer");
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        visualizerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawVisualizer(g);
            }
        };

        visualizerPanel.setBackground(Color.BLACK);

        // Create buttons
        selectFileButton = new JButton("Select File");
        playButton = new JButton("Play");
        stopButton = new JButton("Stop");

        // Set up button actions
        selectFileButton.addActionListener(e -> selectFile());
        playButton.addActionListener(e -> {
            if (selectedAudioFile != null) {
                playAudio(selectedAudioFile);
            } else {
                JOptionPane.showMessageDialog(frame, "Please select an audio file first.");
            }
        });
        stopButton.addActionListener(e -> stopAudio());

        // Layout for buttons
        JPanel controlPanel = new JPanel();
        controlPanel.add(selectFileButton);
        controlPanel.add(playButton);
        controlPanel.add(stopButton);

        frame.add(controlPanel, BorderLayout.NORTH);
        frame.add(visualizerPanel, BorderLayout.CENTER);

        updateTimer = new Timer(TIMER_INTERVAL_MS, e -> updateVisualizer());

        frame.setVisible(true);
    }

    private void selectFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select an Audio File");
        fileChooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("Audio Files", "wav", "mp3", "aiff"));
        int returnValue = fileChooser.showOpenDialog(frame);
        if (returnValue == JFileChooser.APPROVE_OPTION) {
            selectedAudioFile = fileChooser.getSelectedFile();
        }
    }

    private void openAudioFile(File audioFile) throws UnsupportedAudioFileException, IOException {
        try {
            audioInputStream = AudioSystem.getAudioInputStream(audioFile);
            AudioFormat format = audioInputStream.getFormat();

            // Prepare audio data for playback
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = audioInputStream.read(buffer, 0, buffer.length)) != -1) {
                audioData = Arrays.copyOf(buffer, bytesRead);
            }

            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            sourceLine = (SourceDataLine) AudioSystem.getLine(info);
            sourceLine.open(format);
            sourceLine.start();
        } catch (LineUnavailableException e) {
            e.printStackTrace();
            throw new IOException("Audio line unavailable", e);
        }
    }

    private void playAudio(File audioFile) {
        if (isPlaying) {
            stopAudio();
        }
        try {
            openAudioFile(audioFile);
            updateTimer.start();
            new Thread(() -> {
                int offset = 0;
                isPlaying = true;
                while (offset < audioData.length && isPlaying) {
                    int bytesToWrite = Math.min(audioData.length - offset, 1024);
                    sourceLine.write(audioData, offset, bytesToWrite);
                    offset += bytesToWrite;
                }
                sourceLine.drain();
                sourceLine.close();
                updateTimer.stop();
                isPlaying = false;
            }).start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopAudio() {
        if (isPlaying) {
            isPlaying = false;
            sourceLine.stop();
            sourceLine.close();
            updateTimer.stop();
        }
    }

    private void updateVisualizer() {
        if (audioData != null && audioData.length > 0) {
            double[] magnitudes = computeFFT(audioData);
            visualizerPanel.repaint();
        }
    }

    private double[] computeFFT(byte[] audioData) {
        if (audioData == null || audioData.length == 0) {
            return new double[0]; // Prevent errors from empty audioData
        }

        int n = audioData.length / 2;
        double[] real = new double[n];
        double[] imag = new double[n];

        // Convert audio bytes to real values
        for (int i = 0; i < n; i++) {
            int low = audioData[2 * i] & 0xFF;
            int high = audioData[2 * i + 1] << 8;
            real[i] = low | high;
            imag[i] = 0;
        }

        // Apply FFT
        fft(real, imag);

        // Calculate magnitudes (frequency bands)
        double[] magnitudes = new double[n / 2];
        for (int i = 0; i < magnitudes.length; i++) {
            magnitudes[i] = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
        }

        return magnitudes;
    }

    private void fft(double[] real, double[] imag) {
        int n = real.length;
        int levels = (int) (Math.log(n) / Math.log(2));

        for (int i = 0; i < n; i++) {
            int j = Integer.reverse(i) >>> (Integer.SIZE - levels);
            if (i < j) {
                double tempReal = real[i];
                double tempImag = imag[i];
                real[i] = real[j];
                imag[i] = imag[j];
                real[j] = tempReal;
                imag[j] = tempImag;
            }
        }

        for (int size = 2; size <= n; size *= 2) {
            double angle = -2 * Math.PI / size;
            double wReal = Math.cos(angle);
            double wImag = Math.sin(angle);

            for (int i = 0; i < n; i += size) {
                double uReal = 1.0;
                double uImag = 0.0;

                for (int j = 0; j < size / 2; j++) {
                    int evenIndex = i + j;
                    int oddIndex = i + j + size / 2;

                    double tempReal = uReal * real[oddIndex] - uImag * imag[oddIndex];
                    double tempImag = uReal * imag[oddIndex] + uImag * real[oddIndex];

                    real[oddIndex] = real[evenIndex] - tempReal;
                    imag[oddIndex] = imag[evenIndex] - tempImag;
                    real[evenIndex] += tempReal;
                    imag[evenIndex] += tempImag;

                    double tempWReal = uReal * wReal - uImag * wImag;
                    uImag = uReal * wImag + uImag * wReal;
                    uReal = tempWReal;
                }
            }
        }
    }

    private void drawVisualizer(Graphics g) {
        if (audioData != null && audioData.length > 0) {
            double[] magnitudes = computeFFT(audioData);
            int barWidth = visualizerPanel.getWidth() / magnitudes.length;

            g.setColor(Color.GREEN);

            for (int i = 0; i < magnitudes.length; i++) {
                int barHeight = (int) (magnitudes[i] * visualizerPanel.getHeight() / 255.0);
                g.fillRect(i * barWidth, visualizerPanel.getHeight() - barHeight, barWidth, barHeight);
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            EnhancedAudioVisualizer visualizer = new EnhancedAudioVisualizer();
            visualizer.frame.setVisible(true);
        });
    }
}
