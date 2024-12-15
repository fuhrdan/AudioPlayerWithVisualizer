import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import javax.sound.sampled.*;
import be.tarsos.dsp.*;
import be.tarsos.dsp.io.jvm.AudioDispatcherFactory;
import be.tarsos.dsp.spectrum.SpectralPeakProcessor;
import be.tarsos.dsp.util.fft.FFT;

public class AudioPlayerWithVisualizer {
    private JFrame frame;
    private JButton selectButton, playButton, pauseButton, stopButton;
    private JFileChooser fileChooser;
    private JPanel visualizerPanel;
    private File audioFile;
    private AudioDispatcher dispatcher;
    private Thread audioThread;
    private volatile boolean playing = false;

    // Frequency bands: 60Hz, 170Hz, 310Hz, 600Hz, 1kHz, 3kHz, 6kHz, 12kHz, 14kHz, 16kHz
    private final int[] bands = {60, 170, 310, 600, 1000, 3000, 6000, 12000, 14000, 16000};
    private final float[] magnitudes = new float[bands.length];

    public static void main(String[] args) {
        SwingUtilities.invokeLater(AudioPlayerWithVisualizer::new);
    }

    public AudioPlayerWithVisualizer() {
        createAndShowGUI();
    }

    private void createAndShowGUI() {
        frame = new JFrame("Audio Player with Visualizer");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 600);
        frame.setLayout(new BorderLayout());

        // Buttons Panel
        JPanel controlPanel = new JPanel();
        selectButton = new JButton("Select File");
        playButton = new JButton("Play");
        pauseButton = new JButton("Pause");
        stopButton = new JButton("Stop");
        controlPanel.add(selectButton);
        controlPanel.add(playButton);
        controlPanel.add(pauseButton);
        controlPanel.add(stopButton);

        frame.add(controlPanel, BorderLayout.NORTH);

        // Visualizer Panel
        visualizerPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                g.setColor(Color.BLUE);

                int barWidth = getWidth() / bands.length;
                for (int i = 0; i < bands.length; i++) {
                    int barHeight = (int) (magnitudes[i] * getHeight());
                    g.fillRect(i * barWidth, getHeight() - barHeight, barWidth - 2, barHeight);
                }
            }
        };
        visualizerPanel.setBackground(Color.BLACK);
        frame.add(visualizerPanel, BorderLayout.CENTER);

        // Add Action Listeners
        selectButton.addActionListener(e -> selectFile());
        playButton.addActionListener(e -> playAudio());
        pauseButton.addActionListener(e -> pauseAudio());
        stopButton.addActionListener(e -> stopAudio());

        frame.setVisible(true);
    }

    private void selectFile() {
        fileChooser = new JFileChooser();
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

        if (playing) {
            return; // Already playing
        }

        playing = true;

        audioThread = new Thread(() -> {
            try {
                dispatcher = AudioDispatcherFactory.fromFile(audioFile, 1024, 512);
                FFT fft = new FFT(1024);
                dispatcher.addAudioProcessor(new SpectralPeakProcessor(1024, fft, (sampleRate, magnitudes, phases, binIndexes) -> {
                    for (int i = 0; i < bands.length; i++) {
                        int index = (int) (bands[i] / (sampleRate / 1024.0));
                        this.magnitudes[i] = magnitudes[index];
                    }
                    visualizerPanel.repaint();
                }));

                dispatcher.run();
            } catch (Exception ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(frame, "Error playing audio: " + ex.getMessage());
            }
        });

        audioThread.start();
    }

    private void pauseAudio() {
        playing = false;
        if (dispatcher != null) {
            dispatcher.stop();
        }
    }

    private void stopAudio() {
        playing = false;
        if (dispatcher != null) {
            dispatcher.stop();
        }
        if (audioThread != null) {
            audioThread.interrupt();
        }
    }
}
