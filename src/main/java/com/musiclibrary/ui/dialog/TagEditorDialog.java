package com.musiclibrary.ui.dialog;

import com.musiclibrary.i18n.I18nManager;
import com.musiclibrary.model.Track;
import com.musiclibrary.model.TrackMetadataUpdate;
import com.musiclibrary.service.cache.ArtworkCache;
import com.musiclibrary.util.FormatUtils;
import com.musiclibrary.util.TimeFormatter;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Modal dialog for editing ID3 metadata tags.
 * Supports both single-track detailed editing and multi-track batch editing.
 */
public class TagEditorDialog extends JDialog {

    private static final String[] COMMON_GENRES = {
            "", "Rock", "Pop", "Jazz", "Classical", "Hip Hop", "Electronic",
            "Metal", "R&B", "Blues", "Folk", "Country", "Soundtrack", "Anime", "J-Pop", "Other"
    };

    private final List<Track> tracks;
    private final boolean isSingleMode;
    private final Consumer<TrackMetadataUpdate> onSaveCallback;

    // Form fields
    private JTextField titleField;
    private JTextField artistField;
    private JTextField albumField;
    private JComboBox<String> genreCombo;
    private JTextField yearField;
    private JTextField trackNumberField;

    // Artwork fields
    private JLabel artworkPreviewLabel;
    private JButton chooseArtworkButton;
    private JButton clearArtworkButton;
    private byte[] pendingArtworkData;
    private String pendingArtworkMimeType = "image/jpeg";
    private boolean pendingClearArtwork = false;

    // Buttons
    private JButton saveButton;
    private JButton cancelButton;

    private boolean saved = false;

    /**
     * Constructs a tag editor dialog for single or multiple tracks.
     *
     * @param owner          parent window
     * @param tracks         list of tracks to edit
     * @param onSaveCallback callback receiving the constructed TrackMetadataUpdate on confirm
     */
    public TagEditorDialog(Window owner, List<Track> tracks, Consumer<TrackMetadataUpdate> onSaveCallback) {
        super(owner, ModalityType.APPLICATION_MODAL);
        this.tracks = (tracks != null) ? tracks : List.of();
        this.isSingleMode = (this.tracks.size() == 1);
        this.onSaveCallback = onSaveCallback;

        if (this.isSingleMode) {
            Track single = this.tracks.getFirst();
            this.pendingArtworkData = (single.getArtworkData() != null) ? single.getArtworkData().clone() : null;
        }

        initializeUi();
    }

    private void initializeUi() {
        I18nManager i18n = I18nManager.getInstance();

        if (isSingleMode) {
            setTitle(i18n.format("tageditor.title.single", tracks.getFirst().getTitle()));
        } else {
            setTitle(i18n.format("tageditor.title.multiple", tracks.size()));
        }

        setLayout(new BorderLayout());
        setMinimumSize(new Dimension(540, 480));
        setPreferredSize(new Dimension(580, 520));
        setLocationRelativeTo(getOwner());

        // Header / hint banner
        if (!isSingleMode) {
            JPanel bannerPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 8));
            bannerPanel.setBackground(new Color(60, 60, 60, 30));
            JLabel hintLabel = new JLabel(i18n.get("tageditor.hint.multiple_retain"));
            hintLabel.setFont(hintLabel.getFont().deriveFont(Font.ITALIC, 11f));
            bannerPanel.add(hintLabel);
            add(bannerPanel, BorderLayout.NORTH);
        }

        // Tabs: Details / Artwork
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab(i18n.get("tageditor.tab.details"), createDetailsPanel());
        tabbedPane.addTab(i18n.get("tageditor.tab.artwork"), createArtworkPanel());

        add(tabbedPane, BorderLayout.CENTER);

        // Bottom action buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 10));
        saveButton = new JButton(i18n.get("tageditor.btn.save"));
        saveButton.setPreferredSize(new Dimension(85, 28));
        saveButton.addActionListener(e -> handleSave());

        cancelButton = new JButton(i18n.get("tageditor.btn.cancel"));
        cancelButton.setPreferredSize(new Dimension(85, 28));
        cancelButton.addActionListener(e -> dispose());

        buttonPanel.add(cancelButton);
        buttonPanel.add(saveButton);
        add(buttonPanel, BorderLayout.SOUTH);

        // Default button & Esc key binding
        getRootPane().setDefaultButton(saveButton);
        getRootPane().registerKeyboardAction(
                e -> dispose(),
                KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
                JComponent.WHEN_IN_FOCUSED_WINDOW
        );

        pack();
    }

    private JPanel createDetailsPanel() {
        I18nManager i18n = I18nManager.getInstance();
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBorder(BorderFactory.createEmptyBorder(12, 16, 12, 16));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 6, 5, 6);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        Track firstTrack = isSingleMode ? tracks.getFirst() : null;

        // 1. Title
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 0.0;
        panel.add(new JLabel(i18n.get("tageditor.field.title")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        titleField = new JTextField();
        if (isSingleMode && firstTrack != null) {
            titleField.setText(firstTrack.getTitle());
        } else {
            titleField.setText(i18n.get("tageditor.hint.multiple_title_disabled"));
            titleField.setEnabled(false);
        }
        panel.add(titleField, gbc);

        // 2. Artist
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.weightx = 0.0;
        panel.add(new JLabel(i18n.get("tageditor.field.artist")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        artistField = new JTextField();
        if (isSingleMode && firstTrack != null) {
            artistField.setText(firstTrack.getArtist());
        } else if (allShareSameValue(Track::getArtist)) {
            artistField.setText(tracks.getFirst().getArtist());
        }
        panel.add(artistField, gbc);

        // 3. Album
        gbc.gridx = 0;
        gbc.gridy = 2;
        gbc.weightx = 0.0;
        panel.add(new JLabel(i18n.get("tageditor.field.album")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        albumField = new JTextField();
        if (isSingleMode && firstTrack != null) {
            albumField.setText(firstTrack.getAlbum());
        } else if (allShareSameValue(Track::getAlbum)) {
            albumField.setText(tracks.getFirst().getAlbum());
        }
        panel.add(albumField, gbc);

        // 4. Genre
        gbc.gridx = 0;
        gbc.gridy = 3;
        gbc.weightx = 0.0;
        panel.add(new JLabel(i18n.get("tageditor.field.genre")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        genreCombo = new JComboBox<>(COMMON_GENRES);
        genreCombo.setEditable(true);
        if (isSingleMode && firstTrack != null) {
            genreCombo.setSelectedItem(firstTrack.getGenre());
        } else if (allShareSameValue(Track::getGenre)) {
            genreCombo.setSelectedItem(tracks.getFirst().getGenre());
        } else {
            genreCombo.setSelectedItem("");
        }
        panel.add(genreCombo, gbc);

        // 5. Year
        gbc.gridx = 0;
        gbc.gridy = 4;
        gbc.weightx = 0.0;
        panel.add(new JLabel(i18n.get("tageditor.field.year")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        yearField = new JTextField();
        if (isSingleMode && firstTrack != null) {
            yearField.setText(firstTrack.getYear() > 0 ? String.valueOf(firstTrack.getYear()) : "");
        } else if (allShareSameValue(t -> String.valueOf(t.getYear()))) {
            int y = tracks.getFirst().getYear();
            yearField.setText(y > 0 ? String.valueOf(y) : "");
        }
        panel.add(yearField, gbc);

        // 6. Track Number
        gbc.gridx = 0;
        gbc.gridy = 5;
        gbc.weightx = 0.0;
        panel.add(new JLabel(i18n.get("tageditor.field.track_number")), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        trackNumberField = new JTextField();
        if (isSingleMode && firstTrack != null) {
            trackNumberField.setText(firstTrack.getTrackNumber() > 0 ? String.valueOf(firstTrack.getTrackNumber()) : "");
        } else {
            trackNumberField.setEnabled(false);
        }
        panel.add(trackNumberField, gbc);

        // 7. Single Mode: Read-only File Information Section
        if (isSingleMode && firstTrack != null) {
            gbc.gridx = 0;
            gbc.gridy = 6;
            gbc.gridwidth = 2;
            gbc.insets = new Insets(16, 6, 4, 6);
            JLabel sectionLabel = new JLabel("— " + i18n.get("tageditor.field.file_path") + " —");
            sectionLabel.setFont(sectionLabel.getFont().deriveFont(Font.BOLD, 11f));
            sectionLabel.setForeground(Color.GRAY);
            panel.add(sectionLabel, gbc);

            gbc.insets = new Insets(4, 6, 4, 6);
            gbc.gridy = 7;
            JLabel pathLabel = new JLabel(firstTrack.getFilePath());
            pathLabel.setFont(pathLabel.getFont().deriveFont(11f));
            panel.add(pathLabel, gbc);

            gbc.gridy = 8;
            String fileInfo = i18n.get("tageditor.field.duration") + " " + TimeFormatter.format(firstTrack.getDurationSeconds())
                    + "   |   " + i18n.get("tageditor.field.file_size") + " " + FormatUtils.formatFileSize(firstTrack.getFileSizeBytes());
            JLabel infoLabel = new JLabel(fileInfo);
            infoLabel.setFont(infoLabel.getFont().deriveFont(11f));
            infoLabel.setForeground(Color.GRAY);
            panel.add(infoLabel, gbc);
        }

        // Fill remaining vertical space
        gbc.gridx = 0;
        gbc.gridy = 9;
        gbc.gridwidth = 2;
        gbc.weighty = 1.0;
        panel.add(Box.createGlue(), gbc);

        return panel;
    }

    private JPanel createArtworkPanel() {
        I18nManager i18n = I18nManager.getInstance();
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));

        // Artwork preview container
        JPanel previewContainer = new JPanel(new FlowLayout(FlowLayout.CENTER));
        artworkPreviewLabel = new JLabel();
        artworkPreviewLabel.setPreferredSize(new Dimension(160, 160));
        artworkPreviewLabel.setHorizontalAlignment(SwingConstants.CENTER);
        artworkPreviewLabel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));
        updateArtworkPreview();
        previewContainer.add(artworkPreviewLabel);
        panel.add(previewContainer, BorderLayout.CENTER);

        // Control buttons
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 12, 8));
        chooseArtworkButton = new JButton(i18n.get("tageditor.btn.choose_artwork"));
        chooseArtworkButton.addActionListener(e -> chooseArtworkImage());

        clearArtworkButton = new JButton(i18n.get("tageditor.btn.clear_artwork"));
        clearArtworkButton.addActionListener(e -> {
            pendingArtworkData = null;
            pendingClearArtwork = true;
            updateArtworkPreview();
        });

        buttonRow.add(chooseArtworkButton);
        buttonRow.add(clearArtworkButton);
        panel.add(buttonRow, BorderLayout.SOUTH);

        return panel;
    }

    private void updateArtworkPreview() {
        if (pendingArtworkData != null && pendingArtworkData.length > 0) {
            try {
                Image scaled = ArtworkCache.getInstance().getImage(null, pendingArtworkData, 150, 150);
                if (scaled != null) {
                    artworkPreviewLabel.setIcon(new ImageIcon(scaled));
                    artworkPreviewLabel.setText(null);
                    return;
                }
            } catch (Exception ignored) {
            }
        }
        artworkPreviewLabel.setIcon(null);
        artworkPreviewLabel.setText(I18nManager.getInstance().get("tageditor.hint.no_artwork"));
    }

    private void chooseArtworkImage() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle(I18nManager.getInstance().get("tageditor.btn.choose_artwork"));
        chooser.setFileFilter(new FileNameExtensionFilter("Image Files (*.jpg, *.jpeg, *.png)", "jpg", "jpeg", "png"));
        chooser.setAcceptAllFileFilterUsed(false);

        int result = chooser.showOpenDialog(this);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = chooser.getSelectedFile();
            if (selectedFile != null && selectedFile.exists()) {
                try {
                    byte[] bytes = Files.readAllBytes(selectedFile.toPath());
                    String name = selectedFile.getName().toLowerCase();
                    String mime = name.endsWith(".png") ? "image/png" : "image/jpeg";

                    this.pendingArtworkData = bytes;
                    this.pendingArtworkMimeType = mime;
                    this.pendingClearArtwork = false;
                    updateArtworkPreview();
                } catch (IOException e) {
                    JOptionPane.showMessageDialog(this,
                            e.getMessage(),
                            I18nManager.getInstance().get("tageditor.status.error"),
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    protected void showValidationError(String message) {
        JOptionPane.showMessageDialog(this,
                message,
                I18nManager.getInstance().get("app.name"),
                JOptionPane.WARNING_MESSAGE);
    }

    protected boolean validateInputs() {
        I18nManager i18n = I18nManager.getInstance();

        // 1. Validate Year
        String yearStr = yearField.getText().trim();
        if (!yearStr.isEmpty()) {
            try {
                int parsedYear = Integer.parseInt(yearStr);
                if (parsedYear < 1000 || parsedYear > 2999) {
                    showValidationError(i18n.get("tageditor.validation.invalid_year"));
                    yearField.requestFocusInWindow();
                    return false;
                }
            } catch (NumberFormatException e) {
                showValidationError(i18n.get("tageditor.validation.invalid_year"));
                yearField.requestFocusInWindow();
                return false;
            }
        }

        // 2. Validate Track Number (single mode)
        if (isSingleMode) {
            String trkStr = trackNumberField.getText().trim();
            if (!trkStr.isEmpty()) {
                try {
                    int parsedTrackNumber = Integer.parseInt(trkStr);
                    if (parsedTrackNumber < 0) {
                        showValidationError(i18n.get("tageditor.validation.invalid_track_number"));
                        trackNumberField.requestFocusInWindow();
                        return false;
                    }
                } catch (NumberFormatException e) {
                    showValidationError(i18n.get("tageditor.validation.invalid_track_number"));
                    trackNumberField.requestFocusInWindow();
                    return false;
                }
            }
        }
        return true;
    }

    private void handleSave() {
        if (!validateInputs()) {
            return;
        }

        int parsedYear = 0;
        String yearStr = yearField.getText().trim();
        if (!yearStr.isEmpty()) {
            parsedYear = Integer.parseInt(yearStr);
        }

        int parsedTrackNumber = 0;
        if (isSingleMode) {
            String trkStr = trackNumberField.getText().trim();
            if (!trkStr.isEmpty()) {
                parsedTrackNumber = Integer.parseInt(trkStr);
            }
        }

        // 3. Build TrackMetadataUpdate
        TrackMetadataUpdate.Builder builder = TrackMetadataUpdate.builder();

        if (isSingleMode) {
            builder.title(titleField.getText().trim());
            builder.artist(artistField.getText().trim());
            builder.album(albumField.getText().trim());
            Object genreObj = genreCombo.getSelectedItem();
            builder.genre(genreObj != null ? genreObj.toString().trim() : "");
            builder.year(parsedYear);
            builder.trackNumber(parsedTrackNumber);

            if (pendingClearArtwork) {
                builder.clearArtwork();
            } else if (pendingArtworkData != null) {
                builder.artwork(pendingArtworkData, pendingArtworkMimeType);
            }
        } else {
            // Multi-track batch mode: only set fields that the user explicitly edited/entered
            String artistText = artistField.getText().trim();
            if (!artistText.isEmpty()) {
                builder.artist(artistText);
            }

            String albumText = albumField.getText().trim();
            if (!albumText.isEmpty()) {
                builder.album(albumText);
            }

            Object genreObj = genreCombo.getSelectedItem();
            String genreText = genreObj != null ? genreObj.toString().trim() : "";
            if (!genreText.isEmpty()) {
                builder.genre(genreText);
            }

            if (!yearStr.isEmpty()) {
                builder.year(parsedYear);
            }

            if (pendingClearArtwork) {
                builder.clearArtwork();
            } else if (pendingArtworkData != null) {
                builder.artwork(pendingArtworkData, pendingArtworkMimeType);
            }
        }

        TrackMetadataUpdate update = builder.build();

        if (onSaveCallback != null) {
            onSaveCallback.accept(update);
        }

        saved = true;
        dispose();
    }

    private boolean allShareSameValue(java.util.function.Function<Track, String> extractor) {
        if (tracks.isEmpty()) {
            return false;
        }
        String first = extractor.apply(tracks.getFirst());
        if (first == null || first.isBlank()) {
            return false;
        }
        for (Track t : tracks) {
            if (!Objects.equals(first, extractor.apply(t))) {
                return false;
            }
        }
        return true;
    }


    public boolean isSaved() {
        return saved;
    }

    // Accessors for unit testing
    public JTextField getTitleField() { return titleField; }
    public JTextField getArtistField() { return artistField; }
    public JTextField getAlbumField() { return albumField; }
    public JComboBox<String> getGenreCombo() { return genreCombo; }
    public JTextField getYearField() { return yearField; }
    public JTextField getTrackNumberField() { return trackNumberField; }
    public JButton getSaveButton() { return saveButton; }
    public JButton getCancelButton() { return cancelButton; }
    public JButton getClearArtworkButton() { return clearArtworkButton; }
}
