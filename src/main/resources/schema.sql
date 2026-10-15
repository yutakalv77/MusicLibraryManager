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
-- 7. 初期データ投入（デフォルトアカウント）
-- ==============================================================================
-- 初回起動時にデフォルトアカウントを作成しておくことで、UI側が単一アカウントモードでもシームレスに動作可能
INSERT OR IGNORE INTO accounts (id, name, created_at, updated_at)
VALUES ('default', 'Default User', datetime('now'), datetime('now'));
