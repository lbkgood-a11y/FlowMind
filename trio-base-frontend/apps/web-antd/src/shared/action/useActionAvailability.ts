import type { ActionApi } from '#/api/action-client';

import { computed, ref } from 'vue';

import { validateActionCandidates } from '#/api/action-client';

function useActionAvailability() {
  const loading = ref(false);
  const availabilityMap = ref(new Map<string, ActionApi.ActionCandidateValidationResult>());

  const visibleActions = computed(() =>
    [...availabilityMap.value.values()].filter((item) => item.visible !== false),
  );

  async function loadAvailability(candidates: ActionApi.ActionCandidate[]) {
    if (candidates.length === 0) {
      availabilityMap.value = new Map();
      return [];
    }
    loading.value = true;
    try {
      const results = await validateActionCandidates(candidates);
      availabilityMap.value = new Map(
        results.map((item) => [availabilityKey(item), item]),
      );
      return results;
    } finally {
      loading.value = false;
    }
  }

  function getAvailability(key: string) {
    return availabilityMap.value.get(key);
  }

  return {
    availabilityMap,
    getAvailability,
    loadAvailability,
    loading,
    visibleActions,
  };
}

function availabilityKey(item: ActionApi.ActionCandidateValidationResult) {
  return item.candidateId || item.actionType || '';
}

export { useActionAvailability };
