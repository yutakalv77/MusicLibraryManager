-- ==============================================================================
-- MusicLibraryManager SQLite Database Schema (DDL)
-- ==============================================================================
-- 設計方針:
-- 1. 音源マスター (tracks):
--    - ディスク上の物理音源メタデータ（ID3タグ等）を一意に管理。
--    - 同一音源ファイルが複数アカウントで重複登録されるのを防ぎ、容量・スキャン負荷を最小化。
-- 2. アカウントマスター (accounts):
--    - アカウント（プロファイル）を管理。
-- 3. アカウント別楽曲関連 (account_tracks):
--    - アカウントと音源マスターを多対多で紐付ける関連テーブル。
--    - 各アカウントが自分のライブラリに取り込んだ曲のみを管理し、別アカウントの曲は完全非表示。
--    - 評価 (rating)、再生回数 (play_count)、取り込み日 (date_added) をアカウント別楽曲エントリとして独立保持。
-- 4. プレイリストフォルダー (playlist_folders):
--    - プレイリストを分類・整理するためのフォルダー階層を管理。
--    - parent_folder_id による多階層（ネスト）フォルダーに対応。
-- 5. プレイリスト (playlists / playlist_tracks):
--    - プレイリストは作成したアカウントおよび所属フォルダーに紐づき、曲順 (position) を保持。
-- ==============================================================================

-- SQLiteの外部キー制約・WALモード（書き込み高速化・同時実行性向上）の推奨設定
PRAGMA foreign_keys = ON;
PRAGMA journal_mode = WAL;

-- ==============================================================================
-- 1. accounts テーブル（アカウントマスター）
-- ==============================================================================
CREATE TABLE IF NOT EXISTS accounts (
    id TEXT PRIMARY KEY,                             -- アカウントID (UUID または 'default')
    name TEXT NOT NULL UNIQUE,                       -- アカウント表示名 (例: 'Default', 'User 1')
    created_at TEXT NOT NULL,                        -- アカウント作成日時 (ISO-8601 UTC)
    updated_at TEXT NOT NULL                         -- アカウント更新日時 (ISO-8601 UTC)
);

-- ==============================================================================
-- 2. tracks テーブル（音源マスター: ファイル重複を防ぎ一意に保持）
-- ==============================================================================
CREATE TABLE IF NOT EXISTS tracks (
    id TEXT PRIMARY KEY,                             -- 音源マスターID (UUID)
    file_path TEXT NOT NULL UNIQUE,                  -- 音源ファイルの絶対パス (一意制約)
    title TEXT,                                      -- 曲名
    artist TEXT,                                    -- アーティスト名
    album_artist TEXT,                              -- アルバムアーティスト名
    album TEXT,                                     -- アルバム名
    genre TEXT,                                     -- ジャンル名
    year INTEGER DEFAULT 0,                         -- リリース年 (例: 2024)
    track_number INTEGER DEFAULT 0,                 -- トラック番号
    disc_number INTEGER DEFAULT 0,                  -- ディスク番号
    bpm INTEGER DEFAULT 0,                          -- BPM (テンポ)
    bitrate INTEGER DEFAULT 0,                      -- ビットレート (kbps)
    duration_seconds INTEGER DEFAULT 0,             -- 再生時間 (秒)
    file_size_bytes INTEGER DEFAULT 0,              -- ファイルサイズ (バイト)
    file_last_modified TEXT NOT NULL,               -- ディスク上の最終更新日時 (差分スキャン高速判定用)
    has_artwork INTEGER DEFAULT 0,                  -- アートワーク有無フラグ (0: なし, 1: あり - 遅延ロード用)
    created_at TEXT NOT NULL,                        -- DB登録日時 (ISO-8601 UTC)
    updated_at TEXT NOT NULL                         -- DB更新日時 (ISO-8601 UTC)
);

-- tracks テーブル用 インデックス
CREATE INDEX IF NOT EXISTS idx_tracks_file_path ON tracks(file_path);
CREATE INDEX IF NOT EXISTS idx_tracks_album ON tracks(album);
CREATE INDEX IF NOT EXISTS idx_tracks_artist ON tracks(artist);

-- ==============================================================================
-- 3. account_tracks テーブル（関連テーブル: アカウント別ライブラリ楽曲エントリ）
-- ==============================================================================
CREATE TABLE IF NOT EXISTS account_tracks (
    id TEXT PRIMARY KEY,                             -- アカウント楽曲エントリ固有ID (UUID)
    account_id TEXT NOT NULL,                        -- 所有アカウントID
    track_id TEXT NOT NULL,                          -- 参照する音源マスターID
    rating INTEGER DEFAULT 0 CHECK (rating BETWEEN 0 AND 5), -- アカウント固有の評価 (0〜5)
    play_count INTEGER DEFAULT 0 CHECK (play_count >= 0),    -- アカウント固有の再生回数 (0以上)
    last_played_at TEXT,                             -- 最終再生日時 (ISO-8601 UTC, 未再生はNULL)
    date_added TEXT NOT NULL,                        -- このアカウントがライブラリに取り込んだ日時 (ISO-8601 UTC)
    created_at TEXT NOT NULL,                        -- レコード作成日時
    updated_at TEXT NOT NULL,                        -- レコード更新日時
    UNIQUE (account_id, track_id),                   -- 1つのアカウント内で同一音源の重複取り込みを防止
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    FOREIGN KEY (track_id) REFERENCES tracks(id) ON DELETE CASCADE
);

-- account_tracks テーブル用 インデックス（アカウント別楽曲検索・結合の爆速化）
CREATE INDEX IF NOT EXISTS idx_account_tracks_account_id ON account_tracks(account_id);
CREATE INDEX IF NOT EXISTS idx_account_tracks_track_id ON account_tracks(track_id);

-- ==============================================================================
-- 4. playlist_folders テーブル（プレイリストフォルダー: 階層管理対応）
-- ==============================================================================
CREATE TABLE IF NOT EXISTS playlist_folders (
    id TEXT PRIMARY KEY,                             -- フォルダーID (UUID)
    account_id TEXT NOT NULL,                        -- フォルダー所有アカウントID
    parent_folder_id TEXT,                           -- 親フォルダーID (NULLならルート階層。多階層ネスト可能)
    name TEXT NOT NULL,                              -- フォルダー表示名 (例: '作業用BGM', '年代別')
    position INTEGER DEFAULT 0,                      -- サイドバー等での並び順 (0, 1, 2...)
    created_at TEXT NOT NULL,                        -- 作成日時
    updated_at TEXT NOT NULL,                        -- 更新日時
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    FOREIGN KEY (parent_folder_id) REFERENCES playlist_folders(id) ON DELETE CASCADE
);

-- playlist_folders テーブル用 インデックス
CREATE INDEX IF NOT EXISTS idx_playlist_folders_account_id ON playlist_folders(account_id);
CREATE INDEX IF NOT EXISTS idx_playlist_folders_parent_id ON playlist_folders(parent_folder_id);

-- ==============================================================================
-- 5. playlists テーブル（アカウント別プレイリストマスター）
-- ==============================================================================
CREATE TABLE IF NOT EXISTS playlists (
    id TEXT PRIMARY KEY,                             -- プレイリストID (UUID)
    account_id TEXT NOT NULL,                        -- プレイリスト所有アカウントID
    folder_id TEXT,                                  -- 所属フォルダーID (NULLならどのフォルダーにも属さないルート階層)
    name TEXT NOT NULL,                              -- プレイリスト名
    position INTEGER DEFAULT 0,                      -- フォルダー内またはルートでの並び順 (0, 1, 2...)
    created_at TEXT NOT NULL,                        -- 作成日時
    updated_at TEXT NOT NULL,                        -- 更新日時
    FOREIGN KEY (account_id) REFERENCES accounts(id) ON DELETE CASCADE,
    FOREIGN KEY (folder_id) REFERENCES playlist_folders(id) ON DELETE SET NULL
);

-- playlists テーブル用 インデックス
CREATE INDEX IF NOT EXISTS idx_playlists_account_id ON playlists(account_id);
CREATE INDEX IF NOT EXISTS idx_playlists_folder_id ON playlists(folder_id);

-- ==============================================================================
-- 6. playlist_tracks テーブル（プレイリスト内 楽曲エントリ & 並び順管理）
-- ==============================================================================
CREATE TABLE IF NOT EXISTS playlist_tracks (
    playlist_id TEXT NOT NULL,                       -- プレイリストID
    account_track_id TEXT NOT NULL,                  -- アカウント楽曲エントリID
    position INTEGER NOT NULL,                       -- プレイリスト内の並び順 (0, 1, 2...)
    PRIMARY KEY (playlist_id, position),
    FOREIGN KEY (playlist_id) REFERENCES playlists(id) ON DELETE CASCADE,
    FOREIGN KEY (account_track_id) REFERENCES account_tracks(id) ON DELETE CASCADE
);

-- playlist_tracks テーブル用 インデックス
CREATE INDEX IF NOT EXISTS idx_playlist_tracks_playlist_id ON playlist_tracks(playlist_id);
CREATE INDEX IF NOT EXISTS idx_playlist_tracks_account_track_id ON playlist_tracks(account_track_id);

-- ==============================================================================
-- 7. genres テーブル（ジャンルマスター）
-- ==============================================================================
CREATE TABLE IF NOT EXISTS genres (
    id TEXT PRIMARY KEY,                             -- ジャンルID (UUID)
    name TEXT NOT NULL UNIQUE COLLATE NOCASE,        -- ジャンル名 (一意制約・大文字小文字不問)
    created_at TEXT NOT NULL,                        -- 登録日時 (ISO-8601 UTC)
    updated_at TEXT NOT NULL                         -- 更新日時 (ISO-8601 UTC)
);

-- ジャンル名検索用インデックス
CREATE INDEX IF NOT EXISTS idx_genres_name ON genres(name COLLATE NOCASE);

-- ==============================================================================
-- 8. 初期データ投入（デフォルトアカウント & ジャンルマスター）
-- ==============================================================================
-- 初回起動時にデフォルトアカウントを作成しておくことで、UI側が単一アカウントモードでもシームレスに動作可能
INSERT OR IGNORE INTO accounts (id, name, created_at, updated_at)
VALUES ('default', 'Default User', datetime('now'), datetime('now'));

INSERT OR IGNORE INTO genres (id, name, created_at, updated_at) VALUES
    ('genre-id3-0', 'Blues', datetime('now'), datetime('now')),
    ('genre-id3-1', 'Classic Rock', datetime('now'), datetime('now')),
    ('genre-id3-2', 'Country', datetime('now'), datetime('now')),
    ('genre-id3-3', 'Dance', datetime('now'), datetime('now')),
    ('genre-id3-4', 'Disco', datetime('now'), datetime('now')),
    ('genre-id3-5', 'Funk', datetime('now'), datetime('now')),
    ('genre-id3-6', 'Grunge', datetime('now'), datetime('now')),
    ('genre-id3-7', 'Hip-Hop', datetime('now'), datetime('now')),
    ('genre-id3-8', 'Jazz', datetime('now'), datetime('now')),
    ('genre-id3-9', 'Metal', datetime('now'), datetime('now')),
    ('genre-id3-10', 'New Age', datetime('now'), datetime('now')),
    ('genre-id3-11', 'Oldies', datetime('now'), datetime('now')),
    ('genre-id3-12', 'Other', datetime('now'), datetime('now')),
    ('genre-id3-13', 'Pop', datetime('now'), datetime('now')),
    ('genre-id3-14', 'R&B', datetime('now'), datetime('now')),
    ('genre-id3-15', 'Rap', datetime('now'), datetime('now')),
    ('genre-id3-16', 'Reggae', datetime('now'), datetime('now')),
    ('genre-id3-17', 'Rock', datetime('now'), datetime('now')),
    ('genre-id3-18', 'Techno', datetime('now'), datetime('now')),
    ('genre-id3-19', 'Industrial', datetime('now'), datetime('now')),
    ('genre-id3-20', 'Alternative', datetime('now'), datetime('now')),
    ('genre-id3-21', 'Ska', datetime('now'), datetime('now')),
    ('genre-id3-22', 'Death Metal', datetime('now'), datetime('now')),
    ('genre-id3-23', 'Pranks', datetime('now'), datetime('now')),
    ('genre-id3-24', 'Soundtrack', datetime('now'), datetime('now')),
    ('genre-id3-25', 'Euro-Techno', datetime('now'), datetime('now')),
    ('genre-id3-26', 'Ambient', datetime('now'), datetime('now')),
    ('genre-id3-27', 'Trip-Hop', datetime('now'), datetime('now')),
    ('genre-id3-28', 'Vocal', datetime('now'), datetime('now')),
    ('genre-id3-29', 'Jazz-funk', datetime('now'), datetime('now')),
    ('genre-id3-30', 'Fusion', datetime('now'), datetime('now')),
    ('genre-id3-31', 'Trance', datetime('now'), datetime('now')),
    ('genre-id3-32', 'Classical', datetime('now'), datetime('now')),
    ('genre-id3-33', 'Instrumental', datetime('now'), datetime('now')),
    ('genre-id3-34', 'Acid', datetime('now'), datetime('now')),
    ('genre-id3-35', 'House', datetime('now'), datetime('now')),
    ('genre-id3-36', 'Game', datetime('now'), datetime('now')),
    ('genre-id3-37', 'Sound Clip', datetime('now'), datetime('now')),
    ('genre-id3-38', 'Gospel', datetime('now'), datetime('now')),
    ('genre-id3-39', 'Noise', datetime('now'), datetime('now')),
    ('genre-id3-40', 'Alt. Rock', datetime('now'), datetime('now')),
    ('genre-id3-41', 'Bass', datetime('now'), datetime('now')),
    ('genre-id3-42', 'Soul', datetime('now'), datetime('now')),
    ('genre-id3-43', 'Punk', datetime('now'), datetime('now')),
    ('genre-id3-44', 'Space', datetime('now'), datetime('now')),
    ('genre-id3-45', 'Meditative', datetime('now'), datetime('now')),
    ('genre-id3-46', 'Instrumental pop', datetime('now'), datetime('now')),
    ('genre-id3-47', 'Instrumental rock', datetime('now'), datetime('now')),
    ('genre-id3-48', 'Ethnic', datetime('now'), datetime('now')),
    ('genre-id3-49', 'Gothic', datetime('now'), datetime('now')),
    ('genre-id3-50', 'Darkwave', datetime('now'), datetime('now')),
    ('genre-id3-51', 'Techno-Industrial', datetime('now'), datetime('now')),
    ('genre-id3-52', 'Electronic', datetime('now'), datetime('now')),
    ('genre-id3-53', 'Pop-folk', datetime('now'), datetime('now')),
    ('genre-id3-54', 'Eurodance', datetime('now'), datetime('now')),
    ('genre-id3-55', 'Dream', datetime('now'), datetime('now')),
    ('genre-id3-56', 'Southern Rock', datetime('now'), datetime('now')),
    ('genre-id3-57', 'Comedy', datetime('now'), datetime('now')),
    ('genre-id3-58', 'Cult', datetime('now'), datetime('now')),
    ('genre-id3-59', 'Gangsta', datetime('now'), datetime('now')),
    ('genre-id3-60', 'Top 40', datetime('now'), datetime('now')),
    ('genre-id3-61', 'Christian Rap', datetime('now'), datetime('now')),
    ('genre-id3-62', 'Pop/Funk', datetime('now'), datetime('now')),
    ('genre-id3-63', 'Jungle', datetime('now'), datetime('now')),
    ('genre-id3-64', 'Native American', datetime('now'), datetime('now')),
    ('genre-id3-65', 'Cabaret', datetime('now'), datetime('now')),
    ('genre-id3-66', 'New Wave', datetime('now'), datetime('now')),
    ('genre-id3-67', 'Psychedelic', datetime('now'), datetime('now')),
    ('genre-id3-68', 'Rave', datetime('now'), datetime('now')),
    ('genre-id3-69', 'Showtunes', datetime('now'), datetime('now')),
    ('genre-id3-70', 'Trailer', datetime('now'), datetime('now')),
    ('genre-id3-71', 'Lo-Fi', datetime('now'), datetime('now')),
    ('genre-id3-72', 'Tribal', datetime('now'), datetime('now')),
    ('genre-id3-73', 'Acid Punk', datetime('now'), datetime('now')),
    ('genre-id3-74', 'Acid Jazz', datetime('now'), datetime('now')),
    ('genre-id3-75', 'Polka', datetime('now'), datetime('now')),
    ('genre-id3-76', 'Retro', datetime('now'), datetime('now')),
    ('genre-id3-77', 'Musical', datetime('now'), datetime('now')),
    ('genre-id3-78', 'Rock & Roll', datetime('now'), datetime('now')),
    ('genre-id3-79', 'Hard Rock', datetime('now'), datetime('now')),
    ('genre-id3-80', 'Folk', datetime('now'), datetime('now')),
    ('genre-id3-81', 'Folk-Rock', datetime('now'), datetime('now')),
    ('genre-id3-82', 'National Folk', datetime('now'), datetime('now')),
    ('genre-id3-83', 'Swing', datetime('now'), datetime('now')),
    ('genre-id3-84', 'Fast Fusion', datetime('now'), datetime('now')),
    ('genre-id3-85', 'Bebob', datetime('now'), datetime('now')),
    ('genre-id3-86', 'Latin', datetime('now'), datetime('now')),
    ('genre-id3-87', 'Revival', datetime('now'), datetime('now')),
    ('genre-id3-88', 'Celtic', datetime('now'), datetime('now')),
    ('genre-id3-89', 'Bluegrass', datetime('now'), datetime('now')),
    ('genre-id3-90', 'Avantgarde', datetime('now'), datetime('now')),
    ('genre-id3-91', 'Gothic Rock', datetime('now'), datetime('now')),
    ('genre-id3-92', 'Progressive Rock', datetime('now'), datetime('now')),
    ('genre-id3-93', 'Psychedelic Rock', datetime('now'), datetime('now')),
    ('genre-id3-94', 'Symphonic Rock', datetime('now'), datetime('now')),
    ('genre-id3-95', 'Slow Rock', datetime('now'), datetime('now')),
    ('genre-id3-96', 'Big Band', datetime('now'), datetime('now')),
    ('genre-id3-97', 'Chorus', datetime('now'), datetime('now')),
    ('genre-id3-98', 'Easy Listening', datetime('now'), datetime('now')),
    ('genre-id3-99', 'Acoustic', datetime('now'), datetime('now')),
    ('genre-id3-100', 'Humour', datetime('now'), datetime('now')),
    ('genre-id3-101', 'Speech', datetime('now'), datetime('now')),
    ('genre-id3-102', 'Chanson', datetime('now'), datetime('now')),
    ('genre-id3-103', 'Opera', datetime('now'), datetime('now')),
    ('genre-id3-104', 'Chamber Music', datetime('now'), datetime('now')),
    ('genre-id3-105', 'Sonata', datetime('now'), datetime('now')),
    ('genre-id3-106', 'Symphony', datetime('now'), datetime('now')),
    ('genre-id3-107', 'Booty Bass', datetime('now'), datetime('now')),
    ('genre-id3-108', 'Primus', datetime('now'), datetime('now')),
    ('genre-id3-109', 'Porn Groove', datetime('now'), datetime('now')),
    ('genre-id3-110', 'Satire', datetime('now'), datetime('now')),
    ('genre-id3-111', 'Slow Jam', datetime('now'), datetime('now')),
    ('genre-id3-112', 'Club', datetime('now'), datetime('now')),
    ('genre-id3-113', 'Tango', datetime('now'), datetime('now')),
    ('genre-id3-114', 'Samba', datetime('now'), datetime('now')),
    ('genre-id3-115', 'Folklore', datetime('now'), datetime('now')),
    ('genre-id3-116', 'Ballad', datetime('now'), datetime('now')),
    ('genre-id3-117', 'Power Ballad', datetime('now'), datetime('now')),
    ('genre-id3-118', 'Rhythmic Soul', datetime('now'), datetime('now')),
    ('genre-id3-119', 'Freestyle', datetime('now'), datetime('now')),
    ('genre-id3-120', 'Duet', datetime('now'), datetime('now')),
    ('genre-id3-121', 'Punk Rock', datetime('now'), datetime('now')),
    ('genre-id3-122', 'Drum Solo', datetime('now'), datetime('now')),
    ('genre-id3-123', 'A capella', datetime('now'), datetime('now')),
    ('genre-id3-124', 'Euro-House', datetime('now'), datetime('now')),
    ('genre-id3-125', 'Dance Hall', datetime('now'), datetime('now')),
    ('genre-id3-126', 'Goa', datetime('now'), datetime('now')),
    ('genre-id3-127', 'Drum & Bass', datetime('now'), datetime('now')),
    ('genre-id3-128', 'Club-House', datetime('now'), datetime('now')),
    ('genre-id3-129', 'Hardcore', datetime('now'), datetime('now')),
    ('genre-id3-130', 'Terror', datetime('now'), datetime('now')),
    ('genre-id3-131', 'Indie', datetime('now'), datetime('now')),
    ('genre-id3-132', 'BritPop', datetime('now'), datetime('now')),
    ('genre-id3-133', 'Afro-punk', datetime('now'), datetime('now')),
    ('genre-id3-134', 'Polsk Punk', datetime('now'), datetime('now')),
    ('genre-id3-135', 'Beat', datetime('now'), datetime('now')),
    ('genre-id3-136', 'Christian gangsta rap', datetime('now'), datetime('now')),
    ('genre-id3-137', 'Heavy Metal', datetime('now'), datetime('now')),
    ('genre-id3-138', 'Black Metal', datetime('now'), datetime('now')),
    ('genre-id3-139', 'Crossover', datetime('now'), datetime('now')),
    ('genre-id3-140', 'Contemporary Christian', datetime('now'), datetime('now')),
    ('genre-id3-141', 'Christian Rock', datetime('now'), datetime('now')),
    ('genre-id3-142', 'Merengue', datetime('now'), datetime('now')),
    ('genre-id3-143', 'Salsa', datetime('now'), datetime('now')),
    ('genre-id3-144', 'Thrash Metal', datetime('now'), datetime('now')),
    ('genre-id3-145', 'Anime', datetime('now'), datetime('now')),
    ('genre-id3-146', 'JPop', datetime('now'), datetime('now')),
    ('genre-id3-147', 'Synthpop', datetime('now'), datetime('now')),
    ('genre-id3-148', 'Abstract', datetime('now'), datetime('now')),
    ('genre-id3-149', 'Art Rock', datetime('now'), datetime('now')),
    ('genre-id3-150', 'Baroque', datetime('now'), datetime('now')),
    ('genre-id3-151', 'Bhangra', datetime('now'), datetime('now')),
    ('genre-id3-152', 'Big beat', datetime('now'), datetime('now')),
    ('genre-id3-153', 'Breakbeat', datetime('now'), datetime('now')),
    ('genre-id3-154', 'Chillout', datetime('now'), datetime('now')),
    ('genre-id3-155', 'Downtempo', datetime('now'), datetime('now')),
    ('genre-id3-156', 'Dub', datetime('now'), datetime('now')),
    ('genre-id3-157', 'EBM', datetime('now'), datetime('now')),
    ('genre-id3-158', 'Eclectic', datetime('now'), datetime('now')),
    ('genre-id3-159', 'Electro', datetime('now'), datetime('now')),
    ('genre-id3-160', 'Electroclash', datetime('now'), datetime('now')),
    ('genre-id3-161', 'Emo', datetime('now'), datetime('now')),
    ('genre-id3-162', 'Experimental', datetime('now'), datetime('now')),
    ('genre-id3-163', 'Garage', datetime('now'), datetime('now')),
    ('genre-id3-164', 'Global', datetime('now'), datetime('now')),
    ('genre-id3-165', 'IDM', datetime('now'), datetime('now')),
    ('genre-id3-166', 'Illbient', datetime('now'), datetime('now')),
    ('genre-id3-167', 'Industro-Goth', datetime('now'), datetime('now')),
    ('genre-id3-168', 'Jam Band', datetime('now'), datetime('now')),
    ('genre-id3-169', 'Krautrock', datetime('now'), datetime('now')),
    ('genre-id3-170', 'Leftfield', datetime('now'), datetime('now')),
    ('genre-id3-171', 'Lounge', datetime('now'), datetime('now')),
    ('genre-id3-172', 'Math Rock', datetime('now'), datetime('now')),
    ('genre-id3-173', 'New Romantic', datetime('now'), datetime('now')),
    ('genre-id3-174', 'Nu-Breakz', datetime('now'), datetime('now')),
    ('genre-id3-175', 'Post-Punk', datetime('now'), datetime('now')),
    ('genre-id3-176', 'Post-Rock', datetime('now'), datetime('now')),
    ('genre-id3-177', 'Psytrance', datetime('now'), datetime('now')),
    ('genre-id3-178', 'Shoegaze', datetime('now'), datetime('now')),
    ('genre-id3-179', 'Space Rock', datetime('now'), datetime('now')),
    ('genre-id3-180', 'Trop Rock', datetime('now'), datetime('now')),
    ('genre-id3-181', 'World Music', datetime('now'), datetime('now')),
    ('genre-id3-182', 'Neoclassical', datetime('now'), datetime('now')),
    ('genre-id3-183', 'Audiobook', datetime('now'), datetime('now')),
    ('genre-id3-184', 'Audio theatre', datetime('now'), datetime('now')),
    ('genre-id3-185', 'Neue Deutsche Welle', datetime('now'), datetime('now')),
    ('genre-id3-186', 'Podcast', datetime('now'), datetime('now')),
    ('genre-id3-187', 'Indie-Rock', datetime('now'), datetime('now')),
    ('genre-id3-188', 'G-Funk', datetime('now'), datetime('now')),
    ('genre-id3-189', 'Dubstep', datetime('now'), datetime('now')),
    ('genre-id3-190', 'Garage Rock', datetime('now'), datetime('now')),
    ('genre-id3-191', 'Psybient', datetime('now'), datetime('now')),
    ('genre-hip-hop', 'Hip Hop', datetime('now'), datetime('now')),
    ('genre-j-pop', 'J-Pop', datetime('now'), datetime('now'));

