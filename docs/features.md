# Kinzo features

Every feature of the Kinzo app, in plain words, for the website's content writer. Website-only
work isn't listed here.

**Status** is one of:

- **Shipped:** in the current app build. (Kinzo isn't in the app stores yet, so "shipped" means
  built and working, not publicly released.)
- **In progress:** being built now, or built in the app but waiting on something else before
  people can use it.
- **Planned:** agreed, not built yet.

**Adding or changing an entry:** when you add or change a user-facing feature, update this file
in the same commit. Put the feature under the section it fits (or add a section), using the same
three lines: a `###` name, a `**Status:**` line, then a short paragraph on what it does for the
user. Change the status when it changes; don't delete shipped features.

## What Kinzo is

Use this to lead anything written about Kinzo. Agreed with Tom in October 2026.

1. **A social workout tracker.** Train with your friends: follow each other, watch each other's
   progress, design workouts for each other, and set goals and chase achievements together, so
   you keep each other accountable.
2. **The fully featured workout app with no paywall.** Everything in the app is free: no
   subscription and no locked features (the AI coach's price is still to be decided).
3. **Built on data and research.** Detailed progress data for every exercise, exercise ratings
   from published studies with the evidence shown, and, if you choose, your anonymised training
   data helping sports science research.

Most of the social side is still to come. Following friends is built and switches on with
sign-in; sending a workout to a friend is next; seeing the workouts and progress of people you
follow comes after sync. The statuses below say exactly where each feature is.

## Training together

Kinzo's main focus. Everything here needs a free account.

### Follow friends
**Status:** In progress

Follow people, as on Instagram: find them by name or username and follow them, without them
having to follow you back. See who follows you and who you follow, remove a follower, or block
someone. Make your account private and you approve each new follower. Share an invite link, and
whoever opens it in Kinzo follows you and is followed back straight away. Built in the app and
the server; it switches on with sign-in once the Kinzo server is live.

### Design a workout for a friend
**Status:** Planned

Build a workout and send it to someone who follows you. It lands in their Library as "From Sam:
Push Day", and they can save their own copy or decline it. Next to be built.

### Feed and profiles
**Status:** Planned

Open the profile of anyone you follow to see their workouts, personal records and progress, and
see their latest workouts in a feed. Body stats and photos are never shared unless you turn
that on. Comes after sync.

### Goals with friends
**Status:** Planned

Set a goal, such as a target lift or a number of workouts a week, on your own or together with
friends, and see how each of you is getting on. Details still to be designed.

### Achievements
**Status:** Planned

Earn achievements for milestones such as a new personal best or a run of training weeks, and
share them with the people who follow you. Details still to be designed.

### Keep each other accountable
**Status:** Planned

See whether the people you train with have trained this week, and give a friend a nudge when
they haven't. Details still to be designed.

## Free for everyone

### No paywall
**Status:** Shipped

Every feature in Kinzo is free to use: no subscription, no trial and no locked extras. Whether
the planned AI coach stays free isn't decided yet.

## Logging workouts

### Offline workout logging
**Status:** Shipped

Start a workout, add exercises and log each set as weight and reps, reps only, time or
distance, then tick it off. Everything is saved on the phone and works with no signal and no
account.

### Ticked sets stand out
**Status:** Shipped

When you tick off a set, the whole row fills with your theme's accent colour and its numbers
switch to the contrasting colour, so finished sets are easy to tell apart at a glance in every
colour theme, light or dark.

### Last time's numbers
**Status:** Shipped

Beside each set you see what you did in the previous session for that exercise, and can copy
it in with a tap.

### Add a set by sliding
**Status:** Shipped

A tap on Add set fills in the last set's weight and reps. Hold it and slide to make the new set
up to 50% lighter or heavier, in 1.25 kg (2.5 lb) steps. A small pill between sets shows how
the weight changed from the set above.

### Change a number by sliding
**Status:** Shipped

Hold a weight, reps or time box for a moment and slide left or right to change it a step at a
time (1.25 kg or 2.5 lb, one rep, five seconds), with a small buzz on each step, so there's no
need to type. A quick tap still opens the keyboard. Being tried out in practice, so it may change.

### Rest timer
**Status:** Shipped

A timer starts between sets and keeps time even if the phone sleeps. Each exercise remembers
its own rest time (or Off), with a default set in Profile, and the rest bar has quick -15 and
+15 second buttons.

### Notes on each exercise
**Status:** Shipped

Write a note under any exercise in a workout, such as a seat height, a form cue or how it felt.
It saves as you type and stays with that workout, so you see it again when you look back at the
workout, edit it, or check the exercise's history. Next time you do the exercise, your last note
shows faintly in the empty box as a reminder.

### Reorder exercises and delete sets
**Status:** Shipped

Drag exercises into a new order mid-workout, and swipe a set left to delete it, with Undo.

### Replace an exercise mid-workout
**Status:** Shipped

Machine taken or something doesn't feel right? Pick "Replace exercise" from an exercise's menu
during a workout and choose another. The new one takes the same place in the workout with the
same number of sets, ready for its own numbers, and shows what you lifted on it last time. If
you'd already ticked sets of the old exercise, Kinzo checks before removing them. Works when
editing a past workout too.

### Exercise instructions mid-workout
**Status:** Shipped

Tap an exercise's name or info button during a workout to see how to do it.

### Scan a machine
**Status:** In progress

Point the camera at a gym machine and Kinzo works out which machine it is and which variation
you're doing, then adds it to your workout. Built in the app; it needs the Kinzo server to be
live and a free account, which are still being set up.

## Workout history

### History by month
**Status:** Shipped

Every finished workout, grouped by month, with a page showing each exercise and set.

### Resume a workout you just finished
**Status:** Shipped

For three hours after finishing, a workout can be resumed: the timer carries on from where it
stopped, without counting the break.

### Edit past workouts
**Status:** Shipped

Fix or fill in any older workout. Progress and trends update to match.

### Repeat a past workout
**Status:** Shipped

Tap Repeat on any workout in your history, or on the workout's own page, to start a new
workout with the same exercises in the same order. Each set comes filled in with the weight
and reps you did that day, warm-ups included, ready to tick off or change. The past workout
stays as it was.

### Rename a workout
**Status:** Shipped

Give any workout a better name, during it or afterwards: tap its name, or use Rename in the
menu, on the workout itself or on a past workout in your history.

## Exercises

### Exercise library
**Status:** Shipped

About 915 exercises with search and filters, each with a simple pictogram of the movement and
nearly all with step-by-step instructions. Variations such as every kind of chest press fold into one row to keep the list
short, and 52 named gym machines list the exercises each one supports.

### Progress charts
**Status:** Shipped

Each exercise's page shows a chart of your progress once you've done it twice: estimated
one-rep max, heaviest set or total volume for weighted lifts, and reps, time or distance for
the rest. Press a point to see its value and date. The page also shows your best set and how
many sessions you've done.

### Trend lines in the exercise list
**Status:** Shipped

A tiny line next to each exercise shows your last 12 sessions at a glance, with an up, down or
flat mark.

### Demo profile
**Status:** Shipped

Switch to a demo profile in Profile → Developer to see Kinzo with four months of made-up
training already logged: push, pull and leg days, Saturday runs, a lighter week, a stalled squat
and the odd missed session. Every progress chart and trend line has something to show straight
away. Your own workouts are kept completely apart and come back when you switch off. Kinzo
restarts to switch (on iPhone, close and reopen it). Meant for testing the alpha.

### Trends for muscles and body regions
**Status:** Planned

The same at-a-glance trend lines for each muscle, muscle group, and upper and lower body, so you
can see which parts of you are getting stronger.

## Planning your training

### Saved workouts
**Status:** Shipped

Build a workout once with its exercises, sets, reps, weights and rest times, then start it in
one tap with everything filled in.

### Ready-made workouts
**Status:** Shipped

Ten workouts to start from straight away: full body, push/pull/legs, upper/lower, dumbbell
only, bodyweight and core. Copy one to make it your own.

### Workout types and splits
**Status:** Shipped

Mark a workout as resistance training, cardio, swimming and more. Resistance workouts get a
split (Push, Upper, or your own), and a name is suggested from the time of day and split, such
as "Morning push".

### Mesocycles (training blocks)
**Status:** Shipped

Group your workouts into blocks of several weeks. Kinzo shows the current block with its dates
and week number, what's up next, and past blocks. Starting the next block can copy the current
workouts so you only change what's different.

### Create a programme
**Status:** Planned

Answer four quick questions (days a week, sex, any body part to prioritise, and session length)
and Kinzo builds a three-month programme of workouts for you. It picks exercises using ratings
of how well each one trains each muscle. Works offline with no account.

## Body and progress

### Progress photos
**Status:** Shipped

Take progress photos by pose (front, side and back), browse them by day, and delete them. They're
kept private inside Kinzo, out of your photo gallery, and never shared with friends.

### Pose outline for progress photos
**Status:** Planned

Prop your phone up and step into a body outline on the screen that shows exactly how to stand
for a front, side or back photo. When you fit the outline and hold still, Kinzo counts down and
takes the photo by itself. After the first time the outline is your own, so every week's photos
match the same pose and distance.

### Weekly body check-in
**Status:** Planned

One quick weekly routine: today's weight, the three outline photos and, if you like, a few tape
measurements, with an optional reminder on the day you choose. Logging just your weight works
any day.

### Body composition changes
**Status:** Planned

From your check-in photos and your weight, Kinzo estimates how much of a change was fat and how
much was muscle, for example "down 2.1 kg, mostly fat". Shown as an estimate with a range, only
once there are a few weeks to compare, and never shared. All worked out on the phone.

### Compare progress photos
**Status:** Planned

Line up photos from different weeks side by side or with a slider, automatically matched for
size and angle, with measured changes to your outline such as "waist 3% narrower". All on the
phone.

## Data and research

### How well each exercise trains each muscle
**Status:** Planned

Every exercise shows which muscles it trains, each rated from 1 to 5 from published research,
with how strong the evidence is and the studies behind it. Kinzo rates from training studies
that measured muscle growth, not from muscle activation (EMG) readings, which don't reliably
predict growth. Each exercise also gets a stability score from 1 to 5: a Smith machine press
holds you steady and puts more of the effort into the muscle, while a dumbbell press asks you
to balance too.

### Share anonymised training data with researchers
**Status:** Planned

Choose to share your training data, with everything that could identify you removed, to help
sports science researchers. It's off unless you turn it on, and you can turn it off again at any
time. The data will only be shared once it meets the UK GDPR standard for anonymisation, so it
can't be traced back to you.

## Accounts and sync

### Optional account
**Status:** In progress

Kinzo works fully without an account. Signing in with Google or Facebook unlocks friends, and
later sync and the AI coach. You pick a username and can sign out or delete your account at any
time. Built in the app; nobody can sign in until the Kinzo server is live.

### Sign in with email and a text code
**Status:** In progress

Sign in with your email and a six-digit code sent by text or WhatsApp, no password. You can
also add a phone to a Google or Facebook account and use codes from then on. Built in the app
and the server; it switches on once the Kinzo server is live and Twilio is set up.

### Sync across devices
**Status:** Planned

Your workouts, plans and history backed up to your account and kept in step across your phones.

### Sign in with Apple
**Status:** Planned

Sign in with your Apple ID on iPhone.

### AI coach
**Status:** Planned

A coach that helps plan and adjust your training. Needs an account.

## Look and feel

### Colour themes
**Status:** Shipped

Seven colour themes (a black-and-white default plus pastels), each in light and dark, picked in
Profile. The whole app changes colour as you tap one. Light and dark can also follow your
phone's setting.

### Kilograms or pounds
**Status:** Shipped

Use whichever unit you prefer; switch any time in Profile.

### Welcome introduction
**Status:** Shipped

A short introduction the first time you open Kinzo: what it is, kilograms or pounds, rest
between sets, and a colour theme, then straight into training or the ready-made workouts. It
can be skipped, and replayed any time from Profile → Help.

### First-time tips
**Status:** Shipped

Each screen explains itself the first time you open it, one tip at a time, and never interrupts
a workout. Replay a screen's tips with its info button, or turn tips off in Profile → Help.

### Works on Android and iPhone
**Status:** Shipped

One app for both Android and iOS.
