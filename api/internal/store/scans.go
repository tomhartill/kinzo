package store

import (
	"context"
	"time"
)

// CountScansSince is how many machine scans a user has made since a time, for the daily limit.
func (s *Store) CountScansSince(ctx context.Context, userID string, since time.Time) (int, error) {
	var n int
	err := s.pool.QueryRow(ctx,
		`SELECT count(*) FROM machine_scans WHERE user_id = $1::uuid AND created_at > $2`,
		userID, since).Scan(&n)
	return n, err
}

// RecordScan logs a scan's outcome. machineID and exerciseID are empty when nothing matched.
func (s *Store) RecordScan(ctx context.Context, userID, machineID, exerciseID, description string) error {
	_, err := s.pool.Exec(ctx,
		`INSERT INTO machine_scans (user_id, machine_id, exercise_id, description)
		 VALUES ($1::uuid, NULLIF($2, '')::uuid, NULLIF($3, '')::uuid, $4)`,
		userID, machineID, exerciseID, description)
	return err
}
