//! Platform-independent download task rules shared by VideoDL clients.

use std::fmt;

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum TaskState {
    Queued,
    Resolving,
    Downloading,
    Processing,
    Completed,
    Failed(String),
    Cancelled,
}

impl TaskState {
    pub fn is_terminal(&self) -> bool {
        matches!(self, Self::Completed | Self::Failed(_) | Self::Cancelled)
    }

    pub fn can_transition_to(&self, next: &TaskState) -> bool {
        use TaskState::*;
        match self {
            Queued => matches!(next, Resolving | Cancelled | Failed(_)),
            Resolving => matches!(next, Downloading | Cancelled | Failed(_)),
            Downloading => matches!(next, Processing | Completed | Cancelled | Failed(_)),
            Processing => matches!(next, Completed | Cancelled | Failed(_)),
            Completed | Failed(_) | Cancelled => false,
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct TransitionError {
    pub from: TaskState,
    pub to: TaskState,
}

impl fmt::Display for TransitionError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "invalid task transition: {:?} -> {:?}", self.from, self.to)
    }
}

impl std::error::Error for TransitionError {}

#[derive(Debug, Clone, PartialEq, Eq)]
pub struct DownloadTask {
    pub id: String,
    pub state: TaskState,
    pub progress: Progress,
}

impl DownloadTask {
    pub fn new(id: impl Into<String>) -> Self {
        Self {
            id: id.into(),
            state: TaskState::Queued,
            progress: Progress::default(),
        }
    }

    pub fn transition(&mut self, next: TaskState) -> Result<(), TransitionError> {
        if !self.state.can_transition_to(&next) {
            return Err(TransitionError {
                from: self.state.clone(),
                to: next,
            });
        }
        self.state = next;
        Ok(())
    }
}

#[derive(Debug, Clone, Default, PartialEq, Eq)]
pub struct Progress {
    pub downloaded_bytes: u64,
    pub total_bytes: Option<u64>,
    pub speed_bytes_per_second: Option<u64>,
    pub eta_seconds: Option<u64>,
}

impl Progress {
    pub fn percent(&self) -> Option<f64> {
        let total = self.total_bytes?;
        if total == 0 {
            return None;
        }
        Some((self.downloaded_bytes.min(total) as f64 / total as f64) * 100.0)
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Quality {
    Best,
    Audio,
    MaxHeight(u16),
}

impl Quality {
    pub fn parse(input: &str) -> Result<Self, &'static str> {
        let value = input.trim().to_ascii_lowercase();
        match value.as_str() {
            "best" => Ok(Self::Best),
            "audio" => Ok(Self::Audio),
            _ => {
                let height = value.parse::<u16>().map_err(|_| "quality must be best, audio or a resolution")?;
                if !(144..=4320).contains(&height) {
                    return Err("resolution must be between 144p and 4320p");
                }
                Ok(Self::MaxHeight(height))
            }
        }
    }
}

/// Makes a portable filename without path separators or control characters.
pub fn safe_filename(input: &str) -> String {
    let mut output = String::with_capacity(input.len());
    for ch in input.trim().chars() {
        if ch.is_control() || matches!(ch, '/' | '\\' | ':' | '*' | '?' | '"' | '<' | '>' | '|') {
            output.push('_');
        } else {
            output.push(ch);
        }
    }
    let output = output.trim_matches(|ch| ch == ' ' || ch == '.').to_string();
    if output.is_empty() || output == "." || output == ".." {
        "videodl-download".to_string()
    } else {
        output.chars().take(180).collect()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn task_moves_through_valid_states() {
        let mut task = DownloadTask::new("task-1");
        task.transition(TaskState::Resolving).unwrap();
        task.transition(TaskState::Downloading).unwrap();
        task.transition(TaskState::Processing).unwrap();
        task.transition(TaskState::Completed).unwrap();
        assert!(task.state.is_terminal());
    }

    #[test]
    fn terminal_state_cannot_restart() {
        let mut task = DownloadTask::new("task-2");
        task.transition(TaskState::Cancelled).unwrap();
        assert!(task.transition(TaskState::Downloading).is_err());
    }

    #[test]
    fn progress_is_bounded() {
        let progress = Progress {
            downloaded_bytes: 120,
            total_bytes: Some(100),
            ..Progress::default()
        };
        assert_eq!(progress.percent(), Some(100.0));
    }

    #[test]
    fn quality_parser_rejects_out_of_range() {
        assert_eq!(Quality::parse("1080"), Ok(Quality::MaxHeight(1080)));
        assert_eq!(Quality::parse("audio"), Ok(Quality::Audio));
        assert!(Quality::parse("9999").is_err());
    }

    #[test]
    fn filenames_cannot_escape_target_directory() {
        assert_eq!(safe_filename("../movie?.mp4"), "_movie_.mp4");
        assert_eq!(safe_filename("   "), "videodl-download");
    }
}
