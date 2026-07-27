import { Page, PageHeader, Row } from '@/src/components/ui';

export default function FoodScreen() {
  return (
    <Page>
      <PageHeader title="Food" subtitle="Food presentation is now driven from the domain package manifest." />
      <Row title="Open Food chat" detail="Ask for a meal plan, shopping list, or recipe summary." href="/chat" />
      <Row title="Review sources" detail="Check source sync and onboarding for this domain." href="/sources" />
      <Row title="Open home" detail="Return to the dashboard for a compact overview." href="/" />
    </Page>
  );
}
