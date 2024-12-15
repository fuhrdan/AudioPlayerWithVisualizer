import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.File;
import javax.sound.sampled.*;

public class AudioPlayerWithCustomFFT {
    private JFrame frame;
    private JButton selectButton, playButton, stopButton;
    private JPanel visualizerPanel;
    private volatile boolean playing = false;
    private File audioFile;
    private float[] magnitudes = new float[10]; // 10 frequency bands

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AudioPlayerWithCustomFFT::new);
    }

    public AudioPlayerWithCustomFFT() {
        createAndShowGUI();
    }

    private void createAndShowGUI() {
        frame = new JFrame("Audio Player with Custom FFT Visualizer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        // Buttons
        JPanel controlPanel = new JPanel();
        selectButton = new JButton("Select File");
        playButton = new JButton("Play");
        stopButton = new JButton("Stop");
        controlPanel.add(selectButton);
        controlPanel.add(playButton);
        controlPanel.add(stopButton);
        frame.add(controlPanel, BorderLayout.NORTH);

        // Visualizer Panel
        visualizerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Color.BLUE);
                int barWidth = getWidth() / magnitudes.length;
                for (int i = 0; i < magnitudes.length; i++) {
                    int barHeight = (int) (magnitudes[i] * getHeight());
                    g.fillRect(i * barWidth, getHeight() - barHeight, barWidth - 2, barHeight);
                }
            }
        };
        visualizerPanel.setBackground(Color.BLACK);
        frame.add(visualizerPanel, BorderLayout.CENTER);

        // Add listeners
        selectButton.addActionListener(this::selectFile);
        playButton.addActionListener(e -> playAudio());
        stopButton.addActionListener(e -> stopAudio());

        frame.setVisible(true);
    }

    private void selectFile(ActionEvent e) {
        JFileChooser fileChooser = new JFileChooser();
        int result = fileChooser.showOpenDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            audioFile = fileChooser.getSelectedFile();
        }
    }

    private void playAudio() {
        if (audioFile == null) {
            JOptionPane.showMessageDialog(frame, "Please select an audio file first.");
            return;
        }

        playing = true;

        new Thread(() -> {
            try (AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(audioFile)) {
                AudioFormat format = audioInputStream.getFormat();
                DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
                SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);

                line.open(format);
                line.start();

                int bufferSize = 1024;
                byte[] buffer = new byte[bufferSize];
                double[] fftBuffer = new double[bufferSize / 2];
                while (playing && audioInputStream.read(buffer) != -1) {
                    // Convert buffer to samples
                    for (int i = 0; i < fftBuffer.length; i++) {
                        fftBuffer[i] = buffer[2 * i] | (buffer[2 * i + 1] << 8); // Combine bytes into 16-bit samples
                    }

                    // Perform FFT
                    double[] fftResult = fft(fftBuffer);

                    // Update magnitudes for visualization
                    updateMagnitudes(fftResult, format.getSampleRate());
                    visualizerPanel.repaint();

                    // Play audio
                    line.write(buffer, 0, buffer.length);
                }

                line.drain();
                line.close();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Error playing audio: " + ex.getMessage());
            }
        }).start();
    }

    private void stopAudio() {
        playing = false;
    }

    private double[] fft(double[] samples) {
        int n = samples.length;
        double[] real = samples.clone();
        double[] imag = new double[n];

        int logN = (int) (Math.log(n) / Math.log(2));
        for (int i = 0; i < n; i++) {
            int reversed = Integer.reverse(i) >>> (32 - logN);
            if (reversed > i) {
                double temp = real[i];
                real[i] = real[reversed];
                real[reversed] = temp;
            }
        }

        for (int s = 1; s <= logN; s++) {
            int m = 1 << s;
            double wmReal = Math.cos(-2 * Math.PI / m);
            double wmImag = Math.sin(-2 * Math.PI / m);

            for (int k = 0; k < n; k += m) {
                double wReal = 1;
                double wImag = 0;

                for (int j = 0; j < m / 2; j++) {
                    double tReal = wReal * real[k + j + m / 2] - wImag * imag[k + j + m / 2];
                    double tImag = wReal * imag[k + j + m / 2] + wImag * real[k + j + m / 2];

                    real[k + j + m / 2] = real[k + j] - tReal;
                    imag[k + j + m / 2] = imag[k + j] - tImag;

                    real[k + j] += tReal;
                    imag[k + j] += tImag;

                    double tempReal = wReal * wmReal - wImag * wmImag;
                    wImag = wReal * wmImag + wImag * wmReal;
                    wReal = tempReal;
                }
            }
        }

        double[] magnitudes = new double[n / 2];
        for (int i = 0; i < magnitudes.length; i++) {
            magnitudes[i] = Math.sqrt(real[i] * real[i] + imag[i] * imag[i]);
        }
        return magnitudes;
    }

    private void updateMagnitudes(double[] fftResult, float sampleRate) {
        int bandsCount = magnitudes.length;
        int samplesPerBand = fftResult.length / bandsCount;
        for (int i = 0; i < bandsCount; i++) {
            double sum = 0;
            for (int j = 0; j < samplesPerBand; j++) {
                sum += fftResult[i * samplesPerBand + j];
            }
            magnitudes[i] = (float) (sum / samplesPerBand / 1000); // Scale for visualization
        }
    }
}
